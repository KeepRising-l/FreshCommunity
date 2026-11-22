package com.freshcommunity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI freshCommunityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FreshCommunity 社区生鲜团购平台 API 文档")
                        .description("基于 Spring Boot 3 + MyBatis-Plus + Spring Security 构建的社区生鲜团购系统在线接口文档，支持 JWT 鉴权直接调试。")
                        .version("1.0.0")
                        .contact(new Contact().name("FreshCommunity Team")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
