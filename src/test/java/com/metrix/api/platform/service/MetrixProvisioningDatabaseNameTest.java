package com.metrix.api.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetrixProvisioningDatabaseNameTest {

    @Test
    void truncatesLongEmpresaToAtlasLimit() {
        String name = MetrixProvisioningService.buildDatabaseName(
                "Carlos Restaurante", "3f3370cf-aaaa-bbbb-cccc-dddddddddddd");
        assertTrue(name.length() <= MetrixProvisioningService.ATLAS_DB_NAME_MAX_BYTES, name);
        assertEquals(38, name.length());
        assertTrue(name.startsWith("metrix_tenant_"));
        assertTrue(name.endsWith("_3f3370cf"));
    }

    @Test
    void keepsShortNames() {
        String name = MetrixProvisioningService.buildDatabaseName("Acme", "abcd1234-xxxx");
        assertEquals("metrix_tenant_acme_abcd1234", name);
        assertTrue(name.length() <= 38);
    }

    @Test
    void blankEmpresaFallsBack() {
        String name = MetrixProvisioningService.buildDatabaseName("   ", "ffffffff-1");
        assertEquals("metrix_tenant_cliente_ffffffff", name);
    }
}
