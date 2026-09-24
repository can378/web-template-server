package com.example.webtemplate.domain.user.controller;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import com.example.webtemplate.domain.user.dto.UserRequests.*;
import com.example.webtemplate.domain.user.dto.UserResponse;
import com.example.webtemplate.domain.user.service.UserService;
import com.example.webtemplate.domain.user.service.UserService.UserPage;
import com.example.webtemplate.global.security.UserSessions;

@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService users;
    private final UserSessions sessions;

    public UserController(UserService users, UserSessions sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public record CsrfResponse(String headerName, String token) { }

    @GetMapping("/auth/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody Signup request) {
        return ResponseEntity.created(URI.create("/api/users/me")).body(users.signup(request));
    }

    @GetMapping("/users/me")
    public UserResponse me(Authentication auth) { return users.me(auth.getName()); }

    @PatchMapping("/users/me")
    public UserResponse update(Authentication auth, @Valid @RequestBody UpdateProfile request) {
        return users.update(auth.getName(), request);
    }

    @PutMapping("/users/me/password")
    public ResponseEntity<Void> password(Authentication auth, @Valid @RequestBody ChangePassword body,
            HttpServletRequest request, HttpServletResponse response) {
        users.changePassword(auth.getName(), body);
        sessions.expireAll(auth, request, response);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> withdraw(Authentication auth, @Valid @RequestBody Withdraw body,
            HttpServletRequest request, HttpServletResponse response) {
        users.withdraw(auth.getName(), body);
        sessions.expireAll(auth, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/users")
    public UserPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.list(page, size);
    }

    @GetMapping("/admin/users/{userId}")
    public UserResponse get(@PathVariable long userId) { return users.get(userId); }
}
