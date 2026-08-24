package com.smartevent.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    @DisplayName("CORS: Cho phép frontend local gọi API")
    void corsConfiguration_AllowsLocalFrontend() {
        SecurityConfig securityConfig =
                new SecurityConfig(null, null, null);

        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(
                        "http://localhost:3000"
                );

        HttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/events"
                );

        CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(
                List.of("http://localhost:3000"),
                configuration.getAllowedOrigins()
        );
        assertTrue(
                configuration.getAllowedMethods().contains("OPTIONS")
        );
        assertTrue(
                configuration.getAllowedHeaders().contains("Authorization")
        );
        assertFalse(configuration.getAllowCredentials());
    }

    @Test
    @DisplayName("CORS: Hỗ trợ nhiều frontend origin")
    void corsConfiguration_SupportsMultipleOrigins() {
        SecurityConfig securityConfig =
                new SecurityConfig(null, null, null);

        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(
                        "http://localhost:3000, https://app.example.com"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/events"
                );

        CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://app.example.com"
                ),
                configuration.getAllowedOrigins()
        );
    }
}