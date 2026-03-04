package com.metrix.api.service;

import com.metrix.api.dto.CreateStoreRequest;
import com.metrix.api.dto.StoreResponse;
import com.metrix.api.dto.UpdateStoreRequest;

import java.util.List;

/**
 * Contrato del módulo de Sucursales (Sprint 11).
 */
public interface StoreService {

    StoreResponse create(CreateStoreRequest request, String createdBy);

    List<StoreResponse> getAll();

    StoreResponse getById(String id);

    StoreResponse update(String id, UpdateStoreRequest request);

    void deactivate(String id);
}
