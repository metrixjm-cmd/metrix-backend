package com.metrix.api.repository;

import com.metrix.api.model.Incident;
import com.metrix.api.model.IncidentSeverity;
import com.metrix.api.model.IncidentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IncidentRepository extends MongoRepository<Incident, String> {

    // ── Portfolio del reportador ──────────────────────────────────────────────
    List<Incident> findByReporterUserIdAndActivoTrue(String reporterUserId);
    List<Incident> findByReporterUserIdAndStatusAndActivoTrue(String reporterUserId, IncidentStatus status);

    // ── Vista gerencial / admin ───────────────────────────────────────────────
    List<Incident> findByStoreIdAndActivoTrue(String storeId);
    List<Incident> findByStoreIdAndStatusAndActivoTrue(String storeId, IncidentStatus status);
    List<Incident> findByStoreIdAndSeverityAndActivoTrue(String storeId, IncidentSeverity severity);

    // ── Conteos para dashboard ────────────────────────────────────────────────
    long countByStoreIdAndStatusAndActivoTrue(String storeId, IncidentStatus status);
    long countByStoreIdAndSeverityAndStatusAndActivoTrue(String storeId, IncidentSeverity severity, IncidentStatus status);
}
