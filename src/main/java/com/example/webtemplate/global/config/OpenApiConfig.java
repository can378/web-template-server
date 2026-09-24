package com.example.webtemplate.global.config;

import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Web Template API").version("v1")
                .description("회원가입 후 인증 > 로그인 API를 실행하세요. 세션 쿠키로 인증하며, "
                        + "Swagger에서 변경 요청을 실행하면 CSRF 토큰을 자동 발급합니다. "
                        + "관리자 API는 ROLE_ADMIN 권한이 필요합니다."));
    }

    // These endpoints are implemented by Spring Security filters, not MVC controllers.
    @Bean
    OpenApiCustomizer authenticationEndpoints() {
        return api -> {
            var credentials = new ObjectSchema();
            credentials.addProperty("loginId", new StringSchema().description("로그인 아이디"));
            credentials.addProperty("password", new StringSchema().format("password").description("비밀번호"));
            credentials.setRequired(List.of("loginId", "password"));
            api.path("/api/auth/login", new PathItem().post(new Operation()
                    .operationId("login").addTagsItem("인증").summary("세션 로그인")
                    .requestBody(new RequestBody().required(true).content(new Content()
                            .addMediaType("application/x-www-form-urlencoded", new MediaType().schema(credentials))))
                    .responses(new ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("로그인 성공"))
                            .addApiResponse("401", new ApiResponse().description("로그인 실패"))
                            .addApiResponse("403", new ApiResponse().description("CSRF 토큰 오류"))
                            .addApiResponse("429", new ApiResponse().description("로그인 횟수 초과")))));
            api.path("/api/auth/logout", new PathItem().post(new Operation()
                    .operationId("logout").addTagsItem("인증").summary("현재 세션 로그아웃")
                    .responses(new ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("로그아웃 완료"))
                            .addApiResponse("403", new ApiResponse().description("CSRF 토큰 오류")))));
        };
    }
}
