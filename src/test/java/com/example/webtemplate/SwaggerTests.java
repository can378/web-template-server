package com.example.webtemplate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerTests {
    @Autowired MockMvc mvc;

    @Test
    void docsRedirectAndUiAssetsArePublic() throws Exception {
        mvc.perform(get("/docs")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/csrf")));
    }

    @Test
    void specIncludesUserAndSessionEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Web Template API"))
                .andExpect(jsonPath("$.paths['/api/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/x-www-form-urlencoded']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
}
