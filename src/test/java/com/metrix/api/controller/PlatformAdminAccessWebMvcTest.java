package com.metrix.api.controller;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.service.PlatformAdminService;
import com.metrix.api.security.JwtAuthenticationFilter;
import com.metrix.api.security.LicenseFeatureFilter;
import com.metrix.api.security.SuspendedInstanceFilter;
import com.metrix.api.service.LicensePackageService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El ADMIN de un restaurante tiene ROLE_ADMIN pero no ROLE_PLATFORM_ADMIN.
 * Solo Admin 0 puede tocar catálogo comercial e instancias.
 */
@WebMvcTest(
        controllers = {LicensePackageController.class, PlatformController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        LicenseFeatureFilter.class,
                        SuspendedInstanceFilter.class
                }
        )
)
@Import(PlatformAdminAccessWebMvcTest.TestSecurityConfig.class)
class PlatformAdminAccessWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
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
                            .requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
                            .requestMatchers("/api/v1/license-packages/**").hasRole("PLATFORM_ADMIN")
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                            .accessDeniedHandler((req, res, e) -> res.sendError(403)))
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LicensePackageService licensePackageService;

    @MockBean
    private PlatformAdminService platformAdminService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tenantAdmin_cannotListLicensePackages() throws Exception {
        mockMvc.perform(get("/api/v1/license-packages")
                        .with(user("TENANTADM").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdmin_canListLicensePackages() throws Exception {
        when(licensePackageService.getAll()).thenReturn(List.of(
                LicensePackageResponse.builder().id("base").nombre("METRIX Base").build()
        ));

        mockMvc.perform(get("/api/v1/license-packages")
                        .with(user("ADMIN001").roles("ADMIN", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void tenantAdmin_cannotListPlatformInstances() throws Exception {
        TenantContext.setPlatformAdmin(false);
        mockMvc.perform(get("/api/v1/platform/instances")
                        .with(user("TENANTADM").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdmin_canListPlatformInstances() throws Exception {
        TenantContext.setPlatformAdmin(true);
        when(platformAdminService.listInstances()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/platform/instances")
                        .with(user("ADMIN001").roles("ADMIN", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }
}
