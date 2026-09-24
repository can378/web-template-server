package com.example.webtemplate.global.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

public final class SecurityResponses {
    private SecurityResponses() { }

    public static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
