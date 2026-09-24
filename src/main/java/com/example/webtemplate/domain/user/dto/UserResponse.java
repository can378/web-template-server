package com.example.webtemplate.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(Long userId, String loginId, String name, String email,
        String status, LocalDateTime lastLoginAt, LocalDateTime createdAt,
        LocalDateTime updatedAt, List<String> roles) {
    public record Summary(Long userId, String loginId, String name, String email, String status,
            LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public Summary summary() {
        return new Summary(userId, loginId, name, email, status, lastLoginAt, createdAt, updatedAt);
    }
}
