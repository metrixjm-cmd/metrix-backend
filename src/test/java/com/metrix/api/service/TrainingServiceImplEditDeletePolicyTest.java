package com.metrix.api.service;

import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingRequest;
import com.metrix.api.model.Training;
import com.metrix.api.model.TrainingProgress;
import com.metrix.api.model.TrainingStatus;
import com.metrix.api.repository.TrainingMaterialRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.TrainingTemplateRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingServiceImplEditDeletePolicyTest {

    @Mock
    private TrainingRepository trainingRepository;
    @Mock
    private TrainingMaterialRepository materialRepository;
    @Mock
    private TrainingTemplateRepository templateRepository;
    @Mock
    private TrainingMaterialService materialService;
    @Mock
    private TrainingTemplateService templateService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TrainingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrainingServiceImpl(
                trainingRepository,
                materialRepository,
                templateRepository,
                materialService,
                templateService,
                userRepository,
                eventPublisher,
                new TrainingStateMachine(),
                new RolePolicy()
        );
    }

    @Test
    void update_rejects_when_training_completed() {
        Training completed = buildTraining("t-completed", TrainingStatus.COMPLETADA);
        when(trainingRepository.findById("t-completed")).thenReturn(Optional.of(completed));

        UpdateTrainingRequest req = buildUpdateRequest();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.update("t-completed", req)
        );

        assertTrue(ex.getMessage().contains("COMPLETADA"));
        verify(trainingRepository, never()).save(any());
    }

    @Test
    void deactivate_rejects_when_training_completed() {
        Training completed = buildTraining("t-completed", TrainingStatus.COMPLETADA);
        when(trainingRepository.findById("t-completed")).thenReturn(Optional.of(completed));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.deactivate("t-completed")
        );

        assertTrue(ex.getMessage().contains("COMPLETADA"));
        verify(trainingRepository, never()).save(any());
    }

    @Test
    void update_allows_programada_training() {
        Training programada = buildTraining("t-programada", TrainingStatus.PROGRAMADA);
        when(trainingRepository.findById("t-programada")).thenReturn(Optional.of(programada));
        when(trainingRepository.save(any(Training.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTrainingRequest req = buildUpdateRequest();

        TrainingResponse updated = service.update("t-programada", req);

        assertEquals("Nuevo título", updated.getTitle());
        verify(trainingRepository).save(any(Training.class));
    }

    @Test
    void deactivate_allows_programada_training() {
        Training programada = buildTraining("t-programada", TrainingStatus.PROGRAMADA);
        when(trainingRepository.findById("t-programada")).thenReturn(Optional.of(programada));
        when(trainingRepository.save(any(Training.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate("t-programada");

        assertFalse(programada.isActivo());
        verify(trainingRepository).save(programada);
    }

    @Test
    void update_allows_enCurso_training() {
        Training enCurso = buildTraining("t-encurso", TrainingStatus.EN_CURSO);
        when(trainingRepository.findById("t-encurso")).thenReturn(Optional.of(enCurso));
        when(trainingRepository.save(any(Training.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTrainingRequest req = buildUpdateRequest();

        TrainingResponse updated = service.update("t-encurso", req);

        assertEquals("Nuevo título", updated.getTitle());
        verify(trainingRepository).save(any(Training.class));
    }

    @Test
    void deactivate_allows_noCompletada_training() {
        Training noCompletada = buildTraining("t-nocompletada", TrainingStatus.NO_COMPLETADA);
        when(trainingRepository.findById("t-nocompletada")).thenReturn(Optional.of(noCompletada));
        when(trainingRepository.save(any(Training.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate("t-nocompletada");

        assertFalse(noCompletada.isActivo());
        verify(trainingRepository).save(noCompletada);
    }

    private Training buildTraining(String id, TrainingStatus status) {
        return Training.builder()
                .id(id)
                .activo(true)
                .title("Título original")
                .description("Descripción original")
                .level(com.metrix.api.model.TrainingLevel.BASICO)
                .storeId("store-1")
                .shift("TODOS")
                .dueAt(Instant.now().plusSeconds(86400))
                .materials(new ArrayList<>())
                .progress(TrainingProgress.builder().status(status).build())
                .build();
    }

    private UpdateTrainingRequest buildUpdateRequest() {
        UpdateTrainingRequest req = new UpdateTrainingRequest();
        req.setTitle("Nuevo título");
        req.setDescription("Nueva descripción");
        req.setLevel(com.metrix.api.model.TrainingLevel.INTERMEDIO);
        req.setStoreId("store-1");
        req.setShift("MATUTINO");
        req.setDueAt(Instant.now().plusSeconds(172800));
        return req;
    }
}
