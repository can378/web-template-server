package com.example.webtemplate.domain.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MenuApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    long role;
    @BeforeEach
    void setup() {
        jdbc.sql("DELETE FROM web_menu_roles").update();
        jdbc.sql("UPDATE web_menus SET parent_menu_id = NULL").update();
        jdbc.sql("DELETE FROM web_menus").update();
        jdbc.sql("INSERT INTO web_roles (role_code, role_name) VALUES ('ROLE_MENU_TEST', 'Menu test')").update();
        role = jdbc.sql("SELECT role_id FROM web_roles WHERE role_code = 'ROLE_MENU_TEST'").query(Long.class).single();
        jdbc.sql("INSERT INTO web_users (login_id, password_hash, name) VALUES ('menu-test', 'unused', 'Menu test')").update();
        jdbc.sql("INSERT INTO web_user_roles (user_id, role_id) SELECT user_id, ? FROM web_users WHERE login_id = 'menu-test'").param(role).update();
    }

    @Test
    void publicAndRoleTreesRespectAncestorsFlagsAndOrder() throws Exception {
        long restricted = seed("restricted", null, 2);
        long child = seed("child", restricted, 0);
        seed("public", null, 1);
        seed("unassigned", null, 3);
        long hidden = seed("hidden", null, 4);
        seed("hidden-child", hidden, 0);
        jdbc.sql("UPDATE web_menus SET is_visible = 0 WHERE menu_id = ?").param(hidden).update();
        long inactive = seed("inactive", null, 5);
        seed("inactive-child", inactive, 0);
        jdbc.sql("UPDATE web_menus SET is_active = 0 WHERE menu_id = ?").param(inactive).update();
        jdbc.sql("INSERT INTO web_menu_roles (menu_id, role_id) VALUES (?, ?)").params(restricted, role).update();
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].label").value("public"))
                .andExpect(jsonPath("$[1].label").value("unassigned"));
        mvc.perform(get("/api/menus").with(user("menu-test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].children[0].id").value(child));
        jdbc.sql("DELETE FROM web_user_roles WHERE role_id = ?").param(role).update();
        mvc.perform(get("/api/menus").with(user("menu-test"))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void administrationRequiresAdminAndCsrf() throws Exception {
        mvc.perform(get("/api/admin/menus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/menus").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/menus").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body("new"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/menus").with(user("member")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("new"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/menus").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void createPatchClearNullableFieldsAndDelete() throws Exception {
        mvc.perform(post("/api/admin/menus").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("new"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.menu.menuCode").value("new"));
        long id = id("new");
        long parent = seed("parent", null, 0);
        edit(id, "{\"parentMenuId\":" + parent + ",\"menuName\":\"Changed\"}", 200);
        edit(id, "{\"parentMenuId\":null,\"menuPath\":null,\"icon\":null}", 200);
        mvc.perform(get("/api/admin/menus/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.menu.menuName").value("Changed"))
                .andExpect(jsonPath("$.menu.parentMenuId").isEmpty()).andExpect(jsonPath("$.menu.menuPath").isEmpty());
        mvc.perform(delete("/api/admin/menus/" + id).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/menus/" + id).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void invalidHierarchyAndInputsDoNotModifyRows() throws Exception {
        long root = seed("root", null, 0);
        long child = seed("child", root, 1);
        edit(root, "{\"parentMenuId\":" + child + "}", 400);
        edit(root, "{\"parentMenuId\":" + root + "}", 400);
        edit(root, "{\"parentMenuId\":99999999}", 400);
        edit(root, "{\"menuName\":null}", 400);
        edit(root, "{\"isVisible\":1}", 400);
        edit(root, "{\"unexpected\":true}", 400);
        edit(root, "{\"menuPath\":\"https://example.com\"}", 400);
        edit(root, "{\"menuCode\":\"child\"}", 409);
        edit(99999999, "{}", 404);
        mvc.perform(delete("/api/admin/menus/" + root).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_menus").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void roleReplacementIsAtomicAndEmptyListMakesMenuPublic() throws Exception {
        long id = seed("private", null, 0);
        roles(id, "{\"roleIds\":[" + role + "," + role + "]}", 200);
        mvc.perform(get("/api/menus")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/menus").with(user("other"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/menus").with(user("menu-test"))).andExpect(jsonPath("$.length()").value(1));
        roles(id, "{\"roleIds\":[99999999]}", 400);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_menu_roles WHERE menu_id = ?").param(id).query(Long.class).single()).isEqualTo(1);
        roles(id, "{\"roleIds\":[null]}", 400);
        roles(id, "{\"roleIds\":[]}", 200);
        mvc.perform(get("/api/menus")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/menus").with(user("other"))).andExpect(jsonPath("$.length()").value(1));
    }
    private void edit(long id, String json, int status) throws Exception {
        mvc.perform(patch("/api/admin/menus/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(status));
    }
    private void roles(long id, String json, int status) throws Exception {
        mvc.perform(put("/api/admin/menus/" + id + "/roles").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(status));
    }
    private long seed(String code, Long parent, int order) {
        jdbc.sql("INSERT INTO web_menus (menu_code, menu_name, parent_menu_id, sort_order) VALUES (?, ?, ?, ?)")
                .params(code, code, parent, order).update();
        return id(code);
    }
    private long id(String code) {
        return jdbc.sql("SELECT menu_id FROM web_menus WHERE menu_code = ?").param(code).query(Long.class).single();
    }
    private String body(String code) {
        return "{\"menuCode\":\"" + code + "\",\"menuName\":\"New\",\"menuPath\":\"/new\",\"sortOrder\":0,"
                + "\"isVisible\":true,\"isActive\":true}";
    }
}
