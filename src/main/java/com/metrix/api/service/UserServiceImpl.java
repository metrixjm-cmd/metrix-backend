package com.metrix.api.service;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UpdateUserRequest;
import com.metrix.api.dto.UserResponse;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementación del módulo de Recursos Humanos — Sprint 9.
 * <p>
 * GERENTE: solo puede listar/editar usuarios de su propio storeId;
 *          no puede modificar el campo {@code roles}.
 * ADMIN: acceso sin restricción de storeId; puede modificar roles y desactivar.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Listar colaboradores ─────────────────────────────────────────────

    @Override
    public List<UserResponse> getUsersByStore(String storeId, String requestorNumeroUsuario) {
        // Validar scope: si el requestor es GERENTE, debe pedir su propio storeId
        User requestor = userRepository.findByNumeroUsuario(requestorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario solicitante no encontrado: " + requestorNumeroUsuario));

        boolean isAdmin = requestor.getRoles() != null && requestor.getRoles().contains(Role.ADMIN);

        if (!isAdmin && !storeId.equals(requestor.getStoreId())) {
            throw new IllegalStateException(
                    "Acceso denegado: solo puede consultar los colaboradores de su propia sucursal.");
        }

        return userRepository.findByStoreIdAndActivoTrue(storeId)
                .stream().map(this::toResponse).toList();
    }

    // ── Perfil individual ────────────────────────────────────────────────

    @Override
    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));
    }

    // ── Crear colaborador ────────────────────────────────────────────────

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByNumeroUsuario(request.getNumeroUsuario())) {
            throw new IllegalArgumentException(
                    "El número de usuario ya está registrado: " + request.getNumeroUsuario());
        }

        User user = User.builder()
                .nombre(request.getNombre())
                .puesto(request.getPuesto())
                .storeId(request.getStoreId())
                .turno(request.getTurno())
                .numeroUsuario(request.getNumeroUsuario())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(request.getRoles())
                .activo(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    // ── Editar colaborador ───────────────────────────────────────────────

    @Override
    public UserResponse updateUser(String id, UpdateUserRequest request, String requestorNumeroUsuario) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));

        User requestor = userRepository.findByNumeroUsuario(requestorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario solicitante no encontrado: " + requestorNumeroUsuario));

        boolean isAdmin = requestor.getRoles() != null && requestor.getRoles().contains(Role.ADMIN);

        // Aplicar campos no-null
        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            user.setNombre(request.getNombre());
        }
        if (request.getPuesto() != null && !request.getPuesto().isBlank()) {
            user.setPuesto(request.getPuesto());
        }
        if (request.getTurno() != null && !request.getTurno().isBlank()) {
            user.setTurno(request.getTurno());
        }
        // Solo ADMIN puede cambiar roles
        if (isAdmin && request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles());
        }

        return toResponse(userRepository.save(user));
    }

    // ── Desactivar colaborador (soft-delete) ─────────────────────────────

    @Override
    public void deactivateUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));

        if (!user.isActivo()) {
            throw new IllegalStateException("El colaborador ya está inactivo.");
        }

        user.setActivo(false);
        userRepository.save(user);
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .puesto(user.getPuesto())
                .storeId(user.getStoreId())
                .turno(user.getTurno())
                .numeroUsuario(user.getNumeroUsuario())
                .roles(user.getRoles())
                .activo(user.isActivo())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
