package com.metrix.api.service;

import com.metrix.api.dto.CreateStoreRequest;
import com.metrix.api.dto.StoreResponse;
import com.metrix.api.dto.UpdateStoreRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.Store;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementación del módulo de Sucursales — Sprint 11.
 * <p>
 * Cada respuesta se enriquece con conteos de usuarios, tareas y capacitaciones
 * activas para la sucursal, permitiendo al frontend mostrar estadísticas sin
 * llamadas adicionales.
 */
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository    storeRepository;
    private final UserRepository     userRepository;
    private final TaskRepository     taskRepository;
    private final TrainingRepository trainingRepository;

    // ── Crear ────────────────────────────────────────────────────────────────

    @Override
    public StoreResponse create(CreateStoreRequest request, String createdBy) {
        if (storeRepository.existsByCodigo(request.getCodigo())) {
            throw new IllegalStateException(
                    "Ya existe una sucursal con el código: " + request.getCodigo());
        }

        Store.StoreBuilder builder = Store.builder()
                .nombre(request.getNombre())
                .codigo(request.getCodigo().toUpperCase())
                .createdBy(createdBy);

        if (request.getDireccion() != null) builder.direccion(request.getDireccion());
        if (request.getTelefono()  != null) builder.telefono(request.getTelefono());
        if (request.getTurnos()    != null && !request.getTurnos().isEmpty()) {
            builder.turnos(request.getTurnos());
        }

        return toResponse(storeRepository.save(builder.build()));
    }

    // ── Listar ───────────────────────────────────────────────────────────────

    @Override
    public List<StoreResponse> getAll() {
        return storeRepository.findByActivoTrue()
                .stream().map(this::toResponse).toList();
    }

    // ── Detalle ──────────────────────────────────────────────────────────────

    @Override
    public StoreResponse getById(String id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));
        return toResponse(store);
    }

    // ── Editar ───────────────────────────────────────────────────────────────

    @Override
    public StoreResponse update(String id, UpdateStoreRequest request) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));

        if (request.getNombre()    != null && !request.getNombre().isBlank()) {
            store.setNombre(request.getNombre());
        }
        if (request.getDireccion() != null) {
            store.setDireccion(request.getDireccion());
        }
        if (request.getTelefono()  != null) {
            store.setTelefono(request.getTelefono());
        }
        if (request.getTurnos()    != null && !request.getTurnos().isEmpty()) {
            store.setTurnos(request.getTurnos());
        }

        return toResponse(storeRepository.save(store));
    }

    // ── Desactivar (soft-delete) ──────────────────────────────────────────────

    @Override
    public void deactivate(String id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));

        if (!store.isActivo()) {
            throw new IllegalStateException("La sucursal ya está inactiva.");
        }

        store.setActivo(false);
        storeRepository.save(store);
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private StoreResponse toResponse(Store store) {
        String storeId = store.getId();
        return StoreResponse.builder()
                .id(storeId)
                .nombre(store.getNombre())
                .codigo(store.getCodigo())
                .direccion(store.getDireccion())
                .telefono(store.getTelefono())
                .turnos(store.getTurnos())
                .activo(store.isActivo())
                .createdBy(store.getCreatedBy())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .totalUsers(userRepository.countByStoreIdAndActivoTrue(storeId))
                .totalTasks(taskRepository.countByStoreIdAndActivoTrue(storeId))
                .totalTrainings(trainingRepository.countByStoreIdAndActivoTrue(storeId))
                .build();
    }
}
