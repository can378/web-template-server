package com.example.webtemplate.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.webtemplate.domain.user.dto.UserRequests.*;
import com.example.webtemplate.domain.user.dto.UserResponse;
import com.example.webtemplate.domain.user.repository.UserRepository;
import com.example.webtemplate.domain.user.repository.UserRepository.Account;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public UserResponse signup(Signup request) {
        checkPasswordLength(request.password());
        long roleId = users.defaultRole().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "기본 회원 권한이 준비되지 않았습니다."));
        long id = users.insert(request.loginId(), encoder.encode(request.password()),
                request.name().strip(), normalizeEmail(request.email()));
        users.assignRole(id, roleId);
        return get(id);
    }

    public UserResponse me(String loginId) { return get(activeAccount(loginId).id()); }

    public UserResponse get(long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    public record UserPage(List<UserResponse.Summary> items, int page, int size, long total) { }

    public UserPage list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1~100이어야 합니다.");
        }
        return new UserPage(users.list(size, (long) page * size), page, size, users.count());
    }

    @Transactional
    public UserResponse update(String loginId, UpdateProfile request) {
        long id = activeAccount(loginId).id();
        users.updateProfile(id, request.name().strip(), normalizeEmail(request.email()));
        return get(id);
    }

    @Transactional
    public void changePassword(String loginId, ChangePassword request) {
        Account account = activeAccount(loginId);
        verifyPassword(request.currentPassword(), account.passwordHash());
        checkPasswordLength(request.newPassword());
        users.updatePassword(account.id(), encoder.encode(request.newPassword()));
    }

    @Transactional
    public void withdraw(String loginId, Withdraw request) {
        Account account = activeAccount(loginId);
        verifyPassword(request.password(), account.passwordHash());
        users.withdraw(account.id());
    }

    private Account activeAccount(String loginId) {
        return users.findAccount(loginId).filter(a -> "ACTIVE".equals(a.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."));
    }

    private void verifyPassword(String raw, String hash) {
        if (!encoder.matches(raw, hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
    }

    private void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.strip();
    }
}
