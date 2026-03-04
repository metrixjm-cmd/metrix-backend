package com.metrix.api.service;

import com.metrix.api.dto.CreateIncidentRequest;
import com.metrix.api.dto.IncidentResponse;
import com.metrix.api.dto.UpdateIncidentStatusRequest;
import com.metrix.api.model.IncidentStatus;

import java.util.List;

public interface IncidentService {

    IncidentResponse create(CreateIncidentRequest request, String reporterNumeroUsuario);

    List<IncidentResponse> getMyIncidents(String reporterNumeroUsuario);

    List<IncidentResponse> getByStore(String storeId);

    List<IncidentResponse> getByStoreAndStatus(String storeId, IncidentStatus status);

    IncidentResponse getById(String incidentId);

    IncidentResponse updateStatus(String incidentId, UpdateIncidentStatusRequest request,
                                   String currentNumeroUsuario);
}
