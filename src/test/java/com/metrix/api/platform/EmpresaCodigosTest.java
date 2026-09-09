package com.metrix.api.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaCodigosTest {

    @Test
    void generateUsesSlugAndHex() {
        String code = EmpresaCodigos.generate("Tacos El Güero", "a3f2c1d9-1111-2222-3333-444444444444");
        assertEquals("TACOSELG-A3F2", code);
        assertTrue(EmpresaCodigos.isValidFormat(code));
        assertFalse(EmpresaCodigos.isPlatform(code));
    }

    @Test
    void neverGeneratesPlatformReserved() {
        String code = EmpresaCodigos.generate("Metrix", "0000");
        assertNotEquals(EmpresaCodigos.PLATFORM, code);
    }

    @Test
    void normalizeUppercases() {
        assertEquals("TACOS-A3F2", EmpresaCodigos.normalize(" tacos-a3f2 "));
        assertTrue(EmpresaCodigos.isPlatform("metrix"));
        assertTrue(EmpresaCodigos.isBlank("  "));
    }

    @Test
    void twoSameNamesDifferentIdsDiffer() {
        String a = EmpresaCodigos.generate("Tacos", "aaaa1111-xxxx");
        String b = EmpresaCodigos.generate("Tacos", "bbbb2222-xxxx");
        assertNotEquals(a, b);
    }
}
