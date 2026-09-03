package com.metrix.api.platform.service;

import com.metrix.api.model.Catalog;
import com.metrix.api.model.Role;
import com.metrix.api.repository.CatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Catálogos mínimos para que un tenant recién provisionado pueda operar RH.
 * Sin esto no se pueden crear GERENTE/EJECUTADOR (validación de puesto).
 */
@Service
@RequiredArgsConstructor
public class TenantCatalogBootstrap {

    private final CatalogRepository catalogRepository;

    public void seedDefaultsIfEmpty() {
        if (!catalogRepository.findByTypeAndActivoTrue("PUESTO").isEmpty()) {
            return;
        }
        savePuesto("Administrador", Role.ADMIN);
        savePuesto("Gerente", Role.GERENTE);
        savePuesto("Cajero", Role.EJECUTADOR);
        savePuesto("Mesero", Role.EJECUTADOR);

        if (catalogRepository.findByTypeAndActivoTrue("TURNO").isEmpty()) {
            saveSimple("TURNO", "MATUTINO");
            saveSimple("TURNO", "VESPERTINO");
            saveSimple("TURNO", "NOCTURNO");
        }
    }

    private void savePuesto(String value, Role role) {
        catalogRepository.save(Catalog.builder()
                .type("PUESTO")
                .value(value)
                .label(value)
                .role(role)
                .activo(true)
                .build());
    }

    private void saveSimple(String type, String value) {
        catalogRepository.save(Catalog.builder()
                .type(type)
                .value(value)
                .label(value)
                .activo(true)
                .build());
    }
}
