package com.example.webtemplate.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.webtemplate.global.security.LoginRateLimiter;
import org.springframework.security.web.csrf.CsrfToken;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionRegistry sessions;
    @Autowired LoginRateLimiter limiter;
    private static final String PASSWORD = "valid-password-123";

    @BeforeEach
    void reset() {
        limiter.clear();
        sessions.getAllPrincipals().forEach(p -> sessions.getAllSessions(p, true)
                .forEach(s -> sessions.removeSessionInformation(s.getSessionId())));
        jdbc.sql("DELETE FROM web_user_roles").update();
        jdbc.sql("DELETE FROM web_users").update();
        jdbc.sql("DELETE FROM web_roles").update();
        jdbc.sql("INSERT INTO web_roles (role_code, role_name) VALUES ('NORMAL_USER', 'Member'), ('ROLE_ADMIN', 'Admin')").update();
    }

    @Test
    void signupHashesPasswordAndOnlyAssignsMemberRole() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("member01", "one@example.com")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("NORMAL_USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String hash = jdbc.sql("SELECT password_hash FROM web_users WHERE login_id = 'member01'").query(String.class).single();
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicatesAndInvalidInputsAreRejected() throws Exception {
        signup("member01", "one@example.com");
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("member01", "two@example.com"))).andExpect(status().isConflict());
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("member02", "one@example.com"))).andExpect(status().isConflict());
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"x\",\"password\":\"short\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_users").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void missingDefaultRoleDoesNotCreatePartialUser() throws Exception {
        jdbc.sql("DELETE FROM web_roles WHERE role_code = 'NORMAL_USER'").update();
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("member01", null))).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_users").query(Long.class).single()).isZero();
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("member01", null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").param("loginId", "member01").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void browserCsrfTokenMustBeRefetchedAfterLogin() throws Exception {
        signup("member01", null);
        MvcResult initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        CsrfToken oldToken = (CsrfToken) initial.getRequest().getAttribute(CsrfToken.class.getName());
        String oldValue = oldToken.getToken();
        mvc.perform(post("/api/auth/login").session(session).header(oldToken.getHeaderName(), oldValue)
                .param("loginId", "member01").param("password", PASSWORD)).andExpect(status().isNoContent());
        mvc.perform(patch("/api/users/me").session(session).header(oldToken.getHeaderName(), oldValue)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isForbidden());
        MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        CsrfToken newToken = (CsrfToken) refreshed.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(patch("/api/users/me").session(session).header(newToken.getHeaderName(), newToken.getToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginRotatesSessionAndLogoutRevokesAuthentication() throws Exception {
        signup("member01", null);
        MockHttpSession before = new MockHttpSession();
        String beforeId = before.getId();
        MvcResult result = mvc.perform(post("/api/auth/login").session(before).with(csrf())
                .param("loginId", "member01").param("password", PASSWORD)).andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(beforeId);
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("member01")).andExpect(jsonPath("$.lastLoginAt").isNotEmpty());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void failedAndInactiveLoginsReturnGenericError() throws Exception {
        signup("member01", null);
        mvc.perform(post("/api/auth/login").with(csrf()).param("loginId", "member01").param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        for (String state : new String[]{"LOCKED", "INACTIVE", "WITHDRAWN"}) {
            jdbc.sql("UPDATE web_users SET status = ?").param(state).update();
            mvc.perform(post("/api/auth/login").with(csrf()).param("loginId", "member01").param("password", PASSWORD))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void memberCannotReadAdminEndpointsOrEscalateThroughProfile() throws Exception {
        signup("member01", null);
        MockHttpSession session = login("member01", PASSWORD);
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"email\":\"updated@example.com\",\"status\":\"ACTIVE\",\"roles\":[\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.roles[0]").value("NORMAL_USER"));
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadPaginatedUsersAndDetail() throws Exception {
        signup("admin01", null);
        jdbc.sql("INSERT INTO web_user_roles SELECT u.user_id, r.role_id, CURRENT_TIMESTAMP FROM web_users u CROSS JOIN web_roles r WHERE r.role_code = 'ROLE_ADMIN'").update();
        MockHttpSession session = login("admin01", PASSWORD);
        mvc.perform(get("/api/admin/users?page=0&size=1").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        long id = jdbc.sql("SELECT user_id FROM web_users").query(Long.class).single();
        mvc.perform(get("/api/admin/users/" + id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(get("/api/admin/users?size=101").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/999999").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndExpiresAllSessions() throws Exception {
        signup("member01", null);
        MockHttpSession first = login("member01", PASSWORD);
        MockHttpSession second = login("member01", PASSWORD);
        mvc.perform(put("/api/users/me/password").session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/me/password").session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isNoContent());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/users/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("loginId", "member01").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login("member01", "new-password-123");
    }

    @Test
    void withdrawalPreservesUserAndRevokesAllSessions() throws Exception {
        signup("member01", null);
        MockHttpSession first = login("member01", PASSWORD);
        MockHttpSession second = login("member01", PASSWORD);
        mvc.perform(delete("/api/users/me").session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT status FROM web_users").query(String.class).single()).isEqualTo("WITHDRAWN");
        mvc.perform(get("/api/users/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("loginId", "member01").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRateLimitReturns429() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.allow("127.0.0.1")).isTrue();
        }
        mvc.perform(post("/api/auth/login").servletPath("/api/auth/login").with(csrf())
                .param("loginId", "missing").param("password", PASSWORD))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void corsAllowsOnlyConfiguredFrontend() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin", "http://localhost:3100")
                .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/users/me").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PATCH")).andExpect(status().isForbidden());
    }

    private void signup(String loginId, String email) throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(loginId, email))).andExpect(status().isCreated());
    }

    private MockHttpSession login(String loginId, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .param("loginId", loginId).param("password", password)).andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private String signupJson(String loginId, String email) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD
                + "\",\"name\":\"Test User\",\"email\":" + (email == null ? "null" : "\"" + email + "\"") + "}";
    }
}
