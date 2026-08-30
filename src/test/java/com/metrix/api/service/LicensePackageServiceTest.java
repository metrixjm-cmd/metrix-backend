package com.metrix.api.service;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.dto.UpdateLicensePackageRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.model.LicensePricingModel;
import com.metrix.api.platform.repository.LicensePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicensePackageServiceTest {

    @Mock private LicensePackageRepository repository;

    @InjectMocks private LicensePackageServiceImpl service;

    private LicensePackage base;

    @BeforeEach
    void setUp() {
        base = LicensePackageSeed.defaults().get(0);
    }

    @Test
    void toggleDestacado_desmarcaLosDemas() {
        LicensePackage pro = LicensePackageSeed.defaults().get(1);
        when(repository.findById("base")).thenReturn(Optional.of(base));
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(base, pro));
        when(repository.save(any(LicensePackage.class))).thenAnswer(inv -> inv.getArgument(0));

        LicensePackageResponse response = service.toggleDestacado("base");

        assertTrue(response.isDestacado());
        verify(repository, atLeastOnce()).save(any(LicensePackage.class));
    }

    @Test
    void update_lanza404SiNoExiste() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        UpdateLicensePackageRequest request = new UpdateLicensePackageRequest();
        assertThrows(ResourceNotFoundException.class, () -> service.update("missing", request));
    }

    @Test
    void resetDefaults_sobrescribeValores() {
        when(repository.findById(any())).thenAnswer(inv -> Optional.of(copy(base)));
        when(repository.save(any(LicensePackage.class))).thenAnswer(inv -> inv.getArgument(0));

        List<LicensePackageResponse> restored = service.resetDefaults();

        assertEquals(4, restored.size());
        ArgumentCaptor<LicensePackage> captor = ArgumentCaptor.forClass(LicensePackage.class);
        verify(repository, atLeast(4)).save(captor.capture());
        assertEquals("METRIX Base", captor.getAllValues().get(0).getNombre());
        assertEquals(LicensePricingModel.PER_BRANCH, captor.getAllValues().get(0).getPricingModel());
    }

    private LicensePackage copy(LicensePackage source) {
        return LicensePackage.builder()
                .id(source.getId())
                .nombre("Modificado")
                .etiqueta(source.getEtiqueta())
                .descripcion(source.getDescripcion())
                .moneda(source.getMoneda())
                .pricingModel(source.getPricingModel())
                .precioMensual(source.getPrecioMensual())
                .precioAnual(source.getPrecioAnual())
                .precioImplementacion(source.getPrecioImplementacion())
                .maxUsuarios(source.getMaxUsuarios())
                .minSucursales(source.getMinSucursales())
                .maxSucursales(source.getMaxSucursales())
                .soporte(source.getSoporte())
                .funciones(source.getFunciones())
                .accent(LicenseAccent.slate)
                .build();
    }
}
