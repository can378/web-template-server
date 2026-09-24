package com.example.webtemplate.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserRequests {
    private UserRequests() { }

    public record Signup(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{4,50}") String loginId,
            @NotBlank @Size(min = 12, max = 64) String password,
            @NotBlank @Size(max = 100) String name,
            @Email @Size(max = 255) String email) { }

    public record UpdateProfile(
            @NotBlank @Size(max = 100) String name,
            @Email @Size(max = 255) String email) { }

    public record ChangePassword(
            @NotBlank @Size(max = 256) String currentPassword,
            @NotBlank @Size(min = 12, max = 64) String newPassword) { }

    public record Withdraw(@NotBlank @Size(max = 256) String password) { }
}
