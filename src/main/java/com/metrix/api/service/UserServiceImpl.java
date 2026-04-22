package com.metrix.api.service;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.ResetUserPasswordRequest;
import com.metrix.api.dto.UpdateUserRequest;
import com.metrix.api.dto.UserResponse;
import com.metrix.api.dto.VerifyAdminPasswordRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.repository.CatalogRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementación del módulo de Recursos Humanos — Sprint 9.
 * <p>
 * GERENTE: solo puede listar/editar usuarios de su propio storeId;
 *          no puede modificar el campo {@code roles};
 *          y solo puede crear usuarios EJECUTADOR en su sucursal.
 * ADMIN: acceso sin restricción de storeId; puede modificar roles y desactivar.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final CatalogRepository catalogRepository;
    private final PasswordEncoder passwordEncoder;
    private final SequenceService sequenceService;

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

        List<User> users = isAdmin
                ? userRepository.findByStoreIdAndActivoTrue(storeId)
                : getManagerOwnedUsers(storeId, requestor);

        return users.stream().map(this::toResponse).toList();
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findByActivoTrue()
                .stream().map(this::toResponse).toList();
    }

    // ── Perfil individual ────────────────────────────────────────────────

    @Override
    public UserResponse getUserById(String id, String requestorNumeroUsuario) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));
        User requestor = resolveRequestor(requestorNumeroUsuario);
        validateProfileScope(target, requestor);
        return toResponse(target);
    }

    // ── Crear colaborador ────────────────────────────────────────────────

    @Override
    public UserResponse createUser(CreateUserRequest request, String requestorNumeroUsuario) {
        User requestor = resolveRequestor(requestorNumeroUsuario);
        boolean isAdmin = hasRole(requestor, Role.ADMIN);
        boolean isGerente = hasRole(requestor, Role.GERENTE);
        String normalizedNombre = normalizeRequiredText(request.getNombre());
        String normalizedEmail = normalizeOptionalEmail(request.getEmail());

        if (!isAdmin && !isGerente) {
            throw new IllegalStateException("Sin permisos para crear colaboradores.");
        }

        // Para GERENTE, la política es canónica:
        // - siempre crea EJECUTADOR
        // - siempre en su misma sucursal (tomada de BD, no del payload)
        Set<Role> effectiveRoles = isAdmin
                ? request.getRoles()
                : Set.of(Role.EJECUTADOR);
        String effectiveStoreId = isAdmin
                ? request.getStoreId()
                : requestor.getStoreId();

        if (effectiveRoles == null || effectiveRoles.isEmpty()) {
            throw new IllegalArgumentException("Debe asignar al menos un rol.");
        }

        assertUniqueUserFieldsForCreate(normalizedNombre, normalizedEmail);

        Role principalRole = !effectiveRoles.isEmpty()
                ? effectiveRoles.iterator().next()
                : null;
        String effectivePuesto = validateAndResolvePuesto(request.getPuesto(), principalRole);

        // Auto-generar folio si no se envía numeroUsuario
        // Prefijo: rol ADMIN/GERENTE tienen prefijo fijo; EJECUTADOR usa el puesto
        String numeroUsuario = request.getNumeroUsuario();
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            numeroUsuario = sequenceService.generateUserFolio(
                    principalRole != null ? principalRole.name() : null,
                    effectivePuesto
            );
        }

        if (userRepository.existsByNumeroUsuario(numeroUsuario)) {
            throw new IllegalArgumentException(
                    "El número de usuario ya está registrado: " + numeroUsuario);
        }

        User user = User.builder()
                .nombre(normalizedNombre)
                .puesto(effectivePuesto)
                .storeId(effectiveStoreId)
                .turno(request.getTurno())
                .numeroUsuario(numeroUsuario)
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(effectiveRoles)
                .email(normalizedEmail)
                .fechaNacimiento(request.getFechaNacimiento())
                .managerOwnerId(isAdmin ? null : requestor.getId())
                .managerOwnerNumeroUsuario(isAdmin ? null : requestor.getNumeroUsuario())
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
        if (!isAdmin) {
            validateProfileScope(user, requestor);
        }

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
        // Solo ADMIN puede cambiar storeId y roles
        if (isAdmin && request.getStoreId() != null && !request.getStoreId().isBlank()) {
            user.setStoreId(request.getStoreId());
        }
        if (isAdmin && request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().isBlank() ? null : request.getEmail());
        }
        if (request.getFechaNacimiento() != null) {
            user.setFechaNacimiento(request.getFechaNacimiento());
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    public void resetUserPassword(String id, ResetUserPasswordRequest request, String requestorNumeroUsuario) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));
        User requestor = resolveRequestor(requestorNumeroUsuario);

        assertValidPrivilegedPassword(requestor, request.getAdminPassword());
        if (!hasRole(requestor, Role.ADMIN)) {
            validateProfileScope(target, requestor);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("La nueva contrasena y la confirmacion no coinciden.");
        }

        target.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(target);
    }

    @Override
    public void verifyAdminPassword(VerifyAdminPasswordRequest request, String requestorNumeroUsuario) {
        User requestor = resolveRequestor(requestorNumeroUsuario);
        assertValidPrivilegedPassword(requestor, request.getAdminPassword());
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

    // ── Eliminar colaborador (hard-delete) ───────────────────────────────

    @Override
    public void deleteUser(String id, String requestorNumeroUsuario) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador no encontrado: " + id));
        User requestor = resolveRequestor(requestorNumeroUsuario);

        if (!hasRole(requestor, Role.ADMIN)) {
            validateProfileScope(target, requestor);
        }
        userRepository.deleteById(id);
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
                .email(user.getEmail())
                .fechaNacimiento(user.getFechaNacimiento())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private User resolveRequestor(String requestorNumeroUsuario) {
        return userRepository.findByNumeroUsuario(requestorNumeroUsuario)
                .or(() -> userRepository.findById(requestorNumeroUsuario))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario solicitante no encontrado: " + requestorNumeroUsuario));
    }

    private void assertValidPrivilegedPassword(User requestor, String adminPassword) {
        if (!hasRole(requestor, Role.ADMIN) && !hasRole(requestor, Role.GERENTE)) {
            throw new IllegalStateException("Solo ADMIN o GERENTE puede regenerar contrasenas.");
        }
        if (!passwordEncoder.matches(adminPassword, requestor.getPassword())) {
            throw new IllegalStateException("La contrasena actual no es correcta.");
        }
    }

    private void validateProfileScope(User target, User requestor) {
        if (target.getId().equals(requestor.getId()) || hasRole(requestor, Role.ADMIN)) {
            return;
        }
        if (!hasRole(requestor, Role.GERENTE)) {
            throw new IllegalStateException("Sin permisos para consultar este colaborador.");
        }
        boolean sameStore = requestor.getStoreId() != null && requestor.getStoreId().equals(target.getStoreId());
        boolean ownedByManager = isOwnedByManager(target, requestor);
        if (!sameStore || !ownedByManager || !hasRole(target, Role.EJECUTADOR)) {
            throw new IllegalStateException("Solo puedes consultar colaboradores de tu propia plantilla.");
        }
    }

    private List<User> getManagerOwnedUsers(String storeId, User manager) {
        Map<String, User> byId = new LinkedHashMap<>();
        List<User> candidates = new ArrayList<>();

        if (manager.getId() != null) {
            candidates.addAll(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(storeId, manager.getId()));
        }
        if (manager.getNumeroUsuario() != null) {
            candidates.addAll(userRepository.findByStoreIdAndManagerOwnerNumeroUsuarioAndActivoTrue(
                    storeId, manager.getNumeroUsuario()));
        }

        candidates.stream()
                .filter(u -> hasRole(u, Role.EJECUTADOR))
                .filter(u -> isOwnedByManager(u, manager))
                .forEach(u -> byId.put(u.getId(), u));
        return new ArrayList<>(byId.values());
    }

    private boolean isOwnedByManager(User target, User manager) {
        boolean byId = manager.getId() != null && manager.getId().equals(target.getManagerOwnerId());
        boolean byNumero = manager.getNumeroUsuario() != null
                && manager.getNumeroUsuario().equals(target.getManagerOwnerNumeroUsuario());
        return byId || byNumero;
    }

    private boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }

    private String validateAndResolvePuesto(String puesto, Role role) {
        String normalizedPuesto = normalizeRequiredText(puesto);
        if (normalizedPuesto.isBlank()) {
            throw new IllegalArgumentException("El puesto es obligatorio.");
        }
        if (role == Role.ADMIN) {
            return PuestoCatalogPolicy.ADMIN_PUESTO;
        }

        Role expectedRole = role != null ? role : PuestoCatalogPolicy.inferRole(normalizedPuesto);
        boolean exists = catalogRepository.findByTypeAndActivoTrue("PUESTO").stream()
                .anyMatch(catalog ->
                        normalizedPuesto.equalsIgnoreCase(catalog.getValue())
                                && PuestoCatalogPolicy.resolveRole(catalog) == expectedRole);

        if (!exists) {
            throw new IllegalArgumentException(
                    "El puesto seleccionado no corresponde al perfil " + expectedRole.name() + ".");
        }
        return normalizedPuesto;
    }

    private void assertUniqueUserFieldsForCreate(String nombre, String email) {
        if (userRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo electrónico.");
        }
    }

    private String normalizeRequiredText(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    private String normalizeOptionalEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
