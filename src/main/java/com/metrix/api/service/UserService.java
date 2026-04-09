package com.metrix.api.service;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UpdateUserRequest;
import com.metrix.api.dto.UserResponse;

import java.util.List;

/**
 * Contrato del módulo de Recursos Humanos — Sprint 9.
 * Gestión CRUD de colaboradores por sucursal con soft-delete.
 */
public interface UserService {

    /**
     * Lista los colaboradores activos de una sucursal.
     * GERENTE solo puede listar su propio storeId.
     */
    List<UserResponse> getUsersByStore(String storeId, String requestorNumeroUsuario);

    /**
     * Obtiene el perfil completo de un colaborador por su MongoDB id.
     */
    UserResponse getUserById(String id);

    /**
     * Crea un nuevo colaborador con password hasheado.
     * ADMIN puede crear cualquier rol.
     * GERENTE solo puede crear EJECUTADOR y únicamente en su sucursal.
     */
    UserResponse createUser(CreateUserRequest request, String requestorNumeroUsuario);

    /**
     * Actualiza campos opcionales del colaborador.
     * GERENTE no puede modificar el campo {@code roles}.
     */
    UserResponse updateUser(String id, UpdateUserRequest request, String requestorNumeroUsuario);

    /** Lista todos los colaboradores activos del sistema. Solo ADMIN. */
    List<UserResponse> getAllUsers();

    /**
     * Soft-delete: marca {@code activo=false}.
     * Solo ADMIN. Los datos históricos se conservan para KPIs.
     */
    void deactivateUser(String id);

    /**
     * Hard-delete: elimina el registro permanentemente de la base de datos.
     * Solo ADMIN.
     */
    void deleteUser(String id);
}
