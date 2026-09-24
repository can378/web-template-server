package com.example.webtemplate.global.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginRateLimiter limiter;

    public LoginRateLimitFilter(LoginRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath())
                && !limiter.allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            SecurityResponses.error(response, 429, "TOO_MANY_LOGIN_ATTEMPTS");
            return;
        }
        chain.doFilter(request, response);
    }
}
