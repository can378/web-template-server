package com.example.webtemplate.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.example.webtemplate.domain.user.repository.UserRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return loginId -> {
            var account = users.findAccount(loginId)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(account.loginId()).password(account.passwordHash())
                    .authorities(users.roles(account.id()).toArray(String[]::new))
                    .disabled(!"ACTIVE".equals(account.status()))
                    .accountLocked("LOCKED".equals(account.status())).build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessions,
            UserRepository users, LoginRateLimiter limiter) throws Exception {
        http.cors(Customizer.withDefaults())
                .addFilterBefore(new LoginRateLimitFilter(limiter), UsernamePasswordAuthenticationFilter.class)
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/docs", "/docs/", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/api/auth/csrf", "/api/menus", "/api/boards/posts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> SecurityResponses.error(res, 401, "UNAUTHORIZED"))
                        .accessDeniedHandler((req, res, ex) -> SecurityResponses.error(res, 403, "FORBIDDEN")))
                .formLogin(login -> login
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("loginId")
                        .successHandler((req, res, auth) -> {
                            users.recordLogin(auth.getName());
                            res.setStatus(204);
                        })
                        .failureHandler((req, res, ex) -> SecurityResponses.error(res, 401, "INVALID_CREDENTIALS")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(5)
                        .sessionRegistry(sessions)
                        .expiredSessionStrategy(event -> SecurityResponses.error(
                                event.getResponse(), 401, "SESSION_EXPIRED")));
        return http.build();
    }
}
