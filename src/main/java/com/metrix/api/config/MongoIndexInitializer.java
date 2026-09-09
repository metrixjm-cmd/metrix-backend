package com.metrix.api.config;

import com.metrix.api.platform.service.EmpresaCodigoAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
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
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;
    private final MongoTemplate platformMongoTemplate;
    private final EmpresaCodigoAllocator empresaCodigoAllocator;

    public MongoIndexInitializer(
            MongoTemplate mongoTemplate,
            @Qualifier("platformMongoTemplate") MongoTemplate platformMongoTemplate,
            EmpresaCodigoAllocator empresaCodigoAllocator) {
        this.mongoTemplate = mongoTemplate;
        this.platformMongoTemplate = platformMongoTemplate;
        this.empresaCodigoAllocator = empresaCodigoAllocator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // notifications: el historial siempre se lee por usuario y ordenado por
        // fecha descendente (findTop50ByUserIdOrderByCreatedAtDesc). Sin este
        // índice cada consulta recorría la colección entera; la entidad lo
        // declaraba con @CompoundIndex, pero esa anotación no crea nada por sí sola.
        ensure(mongoTemplate, "notifications", new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("created_at", Sort.Direction.DESC)
                .named("idx_notif_user"));

        try {
            int backfilled = empresaCodigoAllocator.backfillMissing();
            if (backfilled > 0) {
                log.info("[MongoIndex] codigoEmpresa asignado a {} instancias", backfilled);
            }
        } catch (Exception e) {
            log.warn("[MongoIndex] no se pudo rellenar codigoEmpresa: {}", e.getMessage());
        }

        dropUniqueNumeroUsuarioIfPresent();
        ensure(platformMongoTemplate, "tenant_admin_index", new Index()
                .on("codigo_empresa", Sort.Direction.ASC)
                .on("numero_usuario", Sort.Direction.ASC)
                .unique()
                .named("idx_tenant_login_codigo_usuario"));
        ensure(platformMongoTemplate, "metrix_instances", new Index()
                .on("codigo_empresa", Sort.Direction.ASC)
                .unique()
                .named("idx_instance_codigo_empresa"));
    }

    private void dropUniqueNumeroUsuarioIfPresent() {
        try {
            var ops = platformMongoTemplate.indexOps("tenant_admin_index");
            for (IndexInfo info : ops.getIndexInfo()) {
                if (info.isUnique()
                        && info.getIndexFields().size() == 1
                        && "numero_usuario".equals(info.getIndexFields().get(0).getKey())) {
                    ops.dropIndex(info.getName());
                    log.info("[MongoIndex] índice único global numero_usuario eliminado: {}", info.getName());
                }
            }
        } catch (Exception e) {
            log.warn("[MongoIndex] no se pudo revisar índices de tenant_admin_index: {}", e.getMessage());
        }
    }

    private void ensure(MongoTemplate template, String collection, Index index) {
        try {
            String name = template.indexOps(collection).ensureIndex(index);
            log.info("[MongoIndex] índice asegurado en '{}': {}", collection, name);
        } catch (Exception e) {
            log.warn("[MongoIndex] no se pudo crear el índice en '{}': {}",
                    collection, e.getMessage());
        }
    }
}
