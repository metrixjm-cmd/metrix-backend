package com.metrix.api.platform.service;

import com.metrix.api.model.Catalog;
import com.metrix.api.model.Role;
import com.metrix.api.repository.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCatalogBootstrapTest {

    @Mock
    private CatalogRepository catalogRepository;

    @InjectMocks
    private TenantCatalogBootstrap bootstrap;

    @Test
    void seedsWhenEmpty() {
        when(catalogRepository.findByTypeAndActivoTrue("PUESTO")).thenReturn(List.of());
        when(catalogRepository.findByTypeAndActivoTrue("TURNO")).thenReturn(List.of());
        when(catalogRepository.save(org.mockito.ArgumentMatchers.any(Catalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        bootstrap.seedDefaultsIfEmpty();

        ArgumentCaptor<Catalog> captor = ArgumentCaptor.forClass(Catalog.class);
        verify(catalogRepository, times(7)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(c ->
                "Gerente".equals(c.getValue()) && c.getRole() == Role.GERENTE));
        assertEquals(4, captor.getAllValues().stream().filter(c -> "PUESTO".equals(c.getType())).count());
    }

    @Test
    void skipsWhenPuestosExist() {
        when(catalogRepository.findByTypeAndActivoTrue("PUESTO")).thenReturn(List.of(
                Catalog.builder().type("PUESTO").value("Gerente").role(Role.GERENTE).build()
        ));

        bootstrap.seedDefaultsIfEmpty();

        verify(catalogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
