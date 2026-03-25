package com.metrix.api.service;

import com.metrix.api.dto.CreateIncidentRequest;
import com.metrix.api.dto.IncidentResponse;
import com.metrix.api.dto.UpdateIncidentStatusRequest;
import com.metrix.api.model.IncidentStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IncidentService {

    IncidentResponse create(CreateIncidentRequest request, String reporterNumeroUsuario);

    List<IncidentResponse> getMyIncidents(String reporterNumeroUsuario);

    List<IncidentResponse> getByStore(String storeId);

    /** Incidencias visibles según la jerarquía del usuario autenticado. */
    List<IncidentResponse> getVisibleForUser(String currentNumeroUsuario);

    List<IncidentResponse> getByStoreAndStatus(String storeId, IncidentStatus status);

    IncidentResponse getById(String incidentId);

    IncidentResponse updateStatus(String incidentId, UpdateIncidentStatusRequest request,
                                   String currentNumeroUsuario);

    IncidentResponse uploadEvidence(String incidentId, MultipartFile file,
                                    String currentNumeroUsuario);

    void deleteAll();
}
