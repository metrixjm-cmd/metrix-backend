package com.metrix.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;
import com.metrix.api.model.TrainingStatus;
import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.UserDetailsServiceImpl;
import com.metrix.api.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrainingController.class)
@Import(TrainingControllerWebMvcTest.TestSecurityConfig.class)
class TrainingControllerWebMvcTest {

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
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) ->
                            res.sendError(401)))
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TrainingService trainingService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void unauthenticated_user_cannot_access_my_trainings() throws Exception {
        mockMvc.perform(get("/api/v1/trainings/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_can_access_get_all_trainings() throws Exception {
        when(trainingService.getAll()).thenReturn(List.of(sampleTraining("t-admin")));

        mockMvc.perform(get("/api/v1/trainings")
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(trainingService).getAll();
    }

    @Test
    void gerente_cannot_access_get_all_trainings() throws Exception {
        mockMvc.perform(get("/api/v1/trainings")
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerente_can_access_trainings_by_store() throws Exception {
        when(userRepository.findByNumeroUsuario("GER001"))
                .thenReturn(Optional.of(User.builder().id("ger-1").storeId("store-1").build()));
        when(trainingService.getByStore("store-1")).thenReturn(List.of(sampleTraining("t-store")));

        mockMvc.perform(get("/api/v1/trainings/store/store-1")
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isOk());

        verify(trainingService).getByStore("store-1");
    }

    @Test
    void gerente_cannot_access_trainings_from_other_store() throws Exception {
        when(userRepository.findByNumeroUsuario("GER001"))
                .thenReturn(Optional.of(User.builder().id("ger-1").storeId("store-1").build()));

        mockMvc.perform(get("/api/v1/trainings/store/store-2")
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ejecutador_can_access_my_trainings() throws Exception {
        when(userRepository.findByNumeroUsuario("EJE001"))
                .thenReturn(Optional.of(User.builder().id("user-eje-1").build()));
        when(trainingService.getMyTrainings("user-eje-1")).thenReturn(List.of(sampleTraining("t-my")));

        mockMvc.perform(get("/api/v1/trainings/my")
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isOk());

        verify(trainingService).getMyTrainings("user-eje-1");
    }

    @Test
    void ejecutador_can_mark_material_as_viewed() throws Exception {
        when(trainingService.markMaterialViewed("t1", "m1", "EJE001"))
                .thenReturn(sampleTraining("t1"));

        mockMvc.perform(patch("/api/v1/trainings/t1/materials/m1/view")
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isOk());

        verify(trainingService).markMaterialViewed("t1", "m1", "EJE001");
    }

    @Test
    void ejecutador_can_update_progress_only_if_service_allows_scope() throws Exception {
        when(trainingService.updateProgress(eq("t1"), any(UpdateTrainingProgressRequest.class), eq("EJE001")))
                .thenReturn(sampleTraining("t1"));

        UpdateTrainingProgressRequest req = new UpdateTrainingProgressRequest(
                TrainingStatus.EN_CURSO, 40, null, null
        );

        mockMvc.perform(patch("/api/v1/trainings/t1/progress")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isOk());

        verify(trainingService).updateProgress(eq("t1"), any(UpdateTrainingProgressRequest.class), eq("EJE001"));
    }

    private TrainingResponse sampleTraining(String id) {
        return TrainingResponse.builder()
                .id(id)
                .title("Capacitación " + id)
                .status(TrainingStatus.PROGRAMADA)
                .percentage(0)
                .storeId("store-1")
                .assignedUserId("user-eje-1")
                .build();
    }
}
