package com.example.webtemplate.domain.health.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.example.webtemplate.global.config.WebConfig;
import com.example.webtemplate.global.security.SecurityConfig;
import com.example.webtemplate.global.security.LoginRateLimiter;
import com.example.webtemplate.domain.user.repository.UserRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(HealthController.class)
@Import({WebConfig.class, SecurityConfig.class, LoginRateLimiter.class})
class HealthControllerTests {

	@MockitoBean
	private UserRepository users;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsOkStatus() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}
}
