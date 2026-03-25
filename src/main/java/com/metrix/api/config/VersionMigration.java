package com.metrix.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-time migration: sets {@code version: 0} on documents created before
 * {@code @Version} was added to the models. Without this field, Spring Data
 * treats existing documents as new (version == null → isNew() == true)
 * and attempts an insert instead of update, causing E11000 duplicate key errors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionMigration {

    private final MongoTemplate mongoTemplate;

    private static final String[] COLLECTIONS = {
            "incidents", "tasks", "stores", "users", "trainings", "exams",
            "training_materials", "training_templates", "question_bank", "exam_templates"
    };

    @EventListener(ApplicationReadyEvent.class)
    public void migrateVersionField() {
        for (String collection : COLLECTIONS) {
            Query query = new Query(Criteria.where("version").exists(false));
            Update update = new Update().set("version", 0L);
            var result = mongoTemplate.updateMulti(query, update, collection);
            if (result.getModifiedCount() > 0) {
                log.info("VersionMigration: set version=0 on {} documents in '{}'",
                        result.getModifiedCount(), collection);
            }
        }
    }
}
