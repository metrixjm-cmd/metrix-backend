package com.metrix.api.repository;

import com.metrix.api.model.Store;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos para {@link Store} (Sprint 11).
 */
@Repository
public interface StoreRepository extends MongoRepository<Store, String> {

    /** Todas las sucursales activas. */
    List<Store> findByActivoTrue();

    /** Busca por código único (para validar duplicados y búsqueda directa). */
    Optional<Store> findByCodigo(String codigo);

    /** Verifica si ya existe una sucursal con ese código antes de crear. */
    boolean existsByCodigo(String codigo);
}
