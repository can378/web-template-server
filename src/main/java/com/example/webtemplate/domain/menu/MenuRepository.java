package com.example.webtemplate.domain.menu;

import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.example.webtemplate.domain.menu.MenuData.*;

@Repository
public class MenuRepository {
    private final JdbcClient jdbc;
    public MenuRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<Row> MAPPER = (rs, n) -> new Row(rs.getLong("menu_id"),
            rs.getObject("parent_menu_id", Long.class), rs.getString("menu_code"), rs.getString("menu_name"),
            rs.getString("menu_path"), rs.getString("icon"), rs.getInt("sort_order"),
            rs.getBoolean("is_visible"), rs.getBoolean("is_active"));
    public List<Row> list() {
        return jdbc.sql("SELECT * FROM web_menus ORDER BY sort_order, menu_id").query(MAPPER).list();
    }
    // Lock before reading/validating a mutation to prevent concurrent hierarchy cycles.
    public List<Row> lockHierarchy() {
        return jdbc.sql("SELECT * FROM web_menus ORDER BY menu_id FOR UPDATE").query(MAPPER).list();
    }
    public List<MenuRole> roles() {
        return jdbc.sql("SELECT menu_id, role_id FROM web_menu_roles ORDER BY role_id")
                .query((rs, n) -> new MenuRole(rs.getLong(1), rs.getLong(2))).list();
    }
    public Set<Long> userRoles(String loginId) {
        return new HashSet<>(jdbc.sql("""
                SELECT ur.role_id FROM web_user_roles ur JOIN web_users u ON u.user_id = ur.user_id
                WHERE u.login_id = ? AND u.status = 'ACTIVE'
                """).param(loginId).query(Long.class).list());
    }
    public boolean roleExists(long id) {
        return jdbc.sql("SELECT COUNT(*) FROM web_roles WHERE role_id = ?").param(id).query(Long.class).single() > 0;
    }
    public long insert(Input input) {
        jdbc.sql("""
                INSERT INTO web_menus (parent_menu_id, menu_code, menu_name, menu_path, icon,
                    sort_order, is_visible, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(values(input)).update();
        return jdbc.sql("SELECT menu_id FROM web_menus WHERE menu_code = ?").param(input.menuCode()).query(Long.class).single();
    }
    public void update(long id, Input input) {
        var args = new ArrayList<>(Arrays.asList(values(input)));
        args.add(id);
        jdbc.sql("""
                UPDATE web_menus SET parent_menu_id = ?, menu_code = ?, menu_name = ?, menu_path = ?,
                    icon = ?, sort_order = ?, is_visible = ?, is_active = ?,
                    updated_at = CURRENT_TIMESTAMP(6) WHERE menu_id = ?
                """).params(args).update();
    }
    private Object[] values(Input i) {
        return new Object[]{i.parentMenuId(), i.menuCode(), i.menuName(), i.menuPath(), i.icon(),
                i.sortOrder(), i.isVisible(), i.isActive()};
    }
    public void replaceRoles(long id, Set<Long> roles) {
        jdbc.sql("DELETE FROM web_menu_roles WHERE menu_id = ?").param(id).update();
        for (long role : roles) jdbc.sql("INSERT INTO web_menu_roles (menu_id, role_id) VALUES (?, ?)").params(id, role).update();
    }
    public void delete(long id) {
        jdbc.sql("DELETE FROM web_menu_roles WHERE menu_id = ?").param(id).update();
        jdbc.sql("DELETE FROM web_menus WHERE menu_id = ?").param(id).update();
    }
}
