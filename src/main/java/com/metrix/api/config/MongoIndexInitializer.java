package com.metrix.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Crea los índices que la aplicación necesita y que no existen en la base.
 * <p>
 * No se usa {@code spring.data.mongodb.auto-index-creation}: la base tiene índices
 * creados a mano con nombres distintos de los que infieren las anotaciones (por
 * ejemplo {@code store_id_idx} en {@code users} frente al {@code store_id} que
 * genera {@code @Indexed}), y Mongo rechaza el duplicado con IndexOptionsConflict,
 * lo que impide arrancar. Crear sólo lo necesario evita tocar esas colecciones.
 * <p>
 * Se ejecuta tras el arranque y captura los errores: un índice que no se pueda
 * crear degrada el rendimiento, pero no debe dejar el servicio abajo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // notifications: el historial siempre se lee por usuario y ordenado por
        // fecha descendente (findTop50ByUserIdOrderByCreatedAtDesc). Sin este
        // índice cada consulta recorría la colección entera; la entidad lo
        // declaraba con @CompoundIndex, pero esa anotación no crea nada por sí sola.
        ensure("notifications", new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("created_at", Sort.Direction.DESC)
                .named("idx_notif_user"));
    }

    private void ensure(String collection, Index index) {
        try {
            String name = mongoTemplate.indexOps(collection).ensureIndex(index);
            log.info("[MongoIndex] índice asegurado en '{}': {}", collection, name);
        } catch (Exception e) {
            log.warn("[MongoIndex] no se pudo crear el índice en '{}': {}",
                     collection, e.getMessage());
        }
    }
}
