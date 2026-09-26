package com.smartevent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("🎟️ Smart Event Ticketing Platform API Documentation")
                        .description("API Smart Event Ticketing: tài khoản, sự kiện, tồn kho, giữ vé, thanh toán VNPay, vé QR, PDF hóa đơn và thông báo bất đồng bộ.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Trịnh Đăng Huy - Backend Engineering Lead")
                                .email("trinhdanghuy29@gmail.com")
                                .url("https://github.com/trinhdanghuy-tech"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán chuỗi Access Token JWT của bạn vào đây để test các API yêu cầu đăng nhập (Không cần gõ chữ 'Bearer ')")));
    }
}
