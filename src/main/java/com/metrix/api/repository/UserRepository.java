package com.metrix.api.repository;

import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link User}.
 * <p>
 * Extiende {@link MongoRepository} para CRUD base + paginación.
 * Los métodos derivados cubren los casos de uso del Sprint 1:
 * <ul>
 *   <li>Login por #Usuario → {@link #findByNumeroUsuario}</li>
 *   <li>Listado por sucursal → {@link #findByStoreIdAndActivoTrue}</li>
 *   <li>Filtro por turno (KPI #5) → {@link #findByStoreIdAndTurnoAndActivoTrue}</li>
 *   <li>Búsqueda por rol → {@link #findByRolesContaining}</li>
 * </ul>
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Busca usuario por su número de operador.
     * Caso de uso principal: autenticación JWT.
     */
    Optional<User> findByNumeroUsuario(String numeroUsuario);

    /**
     * Verifica existencia de #Usuario antes de registrar.
     * Previene duplicados a nivel de aplicación.
     */
    boolean existsByNumeroUsuario(String numeroUsuario);

    /**
     * Usuarios activos de una sucursal.
     * Base para: delegación de tareas, reportes gerenciales.
     */
    List<User> findByStoreIdAndActivoTrue(String storeId);

    /**
     * Usuarios activos filtrados por sucursal y turno.
     * Alimenta el KPI #5: Cumplimiento por Turno.
     */
    List<User> findByStoreIdAndTurnoAndActivoTrue(String storeId, String turno);

    /**
     * Usuarios por rol.
     * Útil para: listar todos los GERENTES, notificaciones a ADMINS, etc.
     */
    List<User> findByRolesContaining(Role role);
}
