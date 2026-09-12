package com.metrix.api.controller;

import com.metrix.api.exception.GlobalExceptionHandler;
import com.metrix.api.platform.service.LicensePasswordResetService;
import com.metrix.api.security.JwtAuthenticationFilter;
import com.metrix.api.security.LicenseFeatureFilter;
import com.metrix.api.security.SuspendedInstanceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthPasswordResetController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        LicenseFeatureFilter.class,
                        SuspendedInstanceFilter.class
                }
        )
)
@Import({AuthPasswordResetControllerWebMvcTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class AuthPasswordResetControllerWebMvcTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        MongoMappingContext mongoMappingContext() {
            return new MongoMappingContext();
        }

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/v1/auth/password-reset/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LicensePasswordResetService licensePasswordResetService;

    @Test
    void request_withoutAuth_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEmpresa\":\"TACOS-A3F2\",\"numeroUsuario\":\"ADMIN001\"}"))
                .andExpect(status().isNoContent());
        verify(licensePasswordResetService).requestReset(any());
    }

    @Test
    void request_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEmpresa\":\"\",\"numeroUsuario\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(licensePasswordResetService, never()).requestReset(any());
    }
}
