package com.example.webtemplate.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class UserSessions {
    private final SessionRegistry sessions;

    public UserSessions(SessionRegistry sessions) { this.sessions = sessions; }

    public void expireAll(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails user && user.getUsername().equals(authentication.getName())) {
                sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
