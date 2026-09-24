package com.example.webtemplate.domain.user.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.webtemplate.domain.user.dto.UserResponse;

@Repository
public class UserRepository {
    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Account(long id, String loginId, String passwordHash, String status) { }

    public Optional<Account> findAccount(String loginId) {
        return jdbc.sql("SELECT user_id, login_id, password_hash, status FROM web_users WHERE login_id = ?")
                .param(loginId).query((rs, row) -> new Account(rs.getLong("user_id"),
                        rs.getString("login_id"), rs.getString("password_hash"), rs.getString("status"))).optional();
    }

    public List<String> roles(long userId) {
        return jdbc.sql("""
                SELECT r.role_code FROM web_roles r
                JOIN web_user_roles ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? ORDER BY r.role_code
                """).param(userId).query(String.class).list();
    }

    public Optional<Long> defaultRole() {
        return jdbc.sql("SELECT role_id FROM web_roles WHERE role_code = 'ROLE_USER'")
                .query(Long.class).optional();
    }

    public long insert(String loginId, String hash, String name, String email) {
        jdbc.sql("INSERT INTO web_users (login_id, password_hash, name, email) VALUES (?, ?, ?, ?)")
                .params(loginId, hash, name, email).update();
        return findAccount(loginId).orElseThrow().id();
    }

    public void assignRole(long userId, long roleId) {
        jdbc.sql("INSERT INTO web_user_roles (user_id, role_id) VALUES (?, ?)")
                .params(userId, roleId).update();
    }

    public Optional<UserResponse> findById(long userId) {
        return jdbc.sql("SELECT * FROM web_users WHERE user_id = ?").param(userId)
                .query(profileMapper()).optional().map(u -> new UserResponse(u.userId(), u.loginId(),
                        u.name(), u.email(), u.status(), u.lastLoginAt(), u.createdAt(), u.updatedAt(), roles(userId)));
    }

    public List<UserResponse.Summary> list(int limit, long offset) {
        // List responses omit role details; the detail endpoint includes them.
        return jdbc.sql("SELECT * FROM web_users ORDER BY user_id DESC LIMIT ? OFFSET ?")
                .params(limit, offset).query(profileMapper()).list().stream().map(UserResponse::summary).toList();
    }

    public long count() { return jdbc.sql("SELECT COUNT(*) FROM web_users").query(Long.class).single(); }

    public void updateProfile(long id, String name, String email) {
        jdbc.sql("UPDATE web_users SET name = ?, email = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")
                .params(name, email, id).update();
    }

    public void updatePassword(long id, String hash) {
        jdbc.sql("UPDATE web_users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")
                .params(hash, id).update();
    }

    public void withdraw(long id) {
        jdbc.sql("UPDATE web_users SET status = 'WITHDRAWN', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")
                .param(id).update();
    }

    public void recordLogin(String loginId) {
        jdbc.sql("UPDATE web_users SET last_login_at = CURRENT_TIMESTAMP WHERE login_id = ?")
                .param(loginId).update();
    }

    private RowMapper<UserResponse> profileMapper() {
        return (rs, row) -> new UserResponse(rs.getLong("user_id"), rs.getString("login_id"),
                rs.getString("name"), rs.getString("email"), rs.getString("status"),
                time(rs.getTimestamp("last_login_at")), time(rs.getTimestamp("created_at")),
                time(rs.getTimestamp("updated_at")), List.of());
    }

    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
