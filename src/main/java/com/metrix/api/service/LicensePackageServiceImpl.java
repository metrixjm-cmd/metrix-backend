package com.metrix.api.service;

import com.metrix.api.dto.LicenseFeatureDto;
import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.dto.UpdateLicensePackageRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.LicenseFeature;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.repository.LicensePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LicensePackageServiceImpl implements LicensePackageService {

    private final LicensePackageRepository repository;

    @Override
    public List<LicensePackageResponse> getAll() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public LicensePackageResponse getById(String id) {
        return toResponse(findEntity(id));
    }

    @Override
    public LicensePackageResponse update(String id, UpdateLicensePackageRequest request) {
        LicensePackage entity = findEntity(id);
        applyUpdate(entity, request);

        if (request.isDestacado()) {
            clearDestacadoExcept(id);
        }

        return toResponse(repository.save(entity));
    }

    @Override
    public LicensePackageResponse toggleActivo(String id) {
        LicensePackage entity = findEntity(id);
        entity.setActivo(!entity.isActivo());
        if (!entity.isActivo()) {
            entity.setDestacado(false);
        }
        return toResponse(repository.save(entity));
    }

    @Override
    public LicensePackageResponse toggleDestacado(String id) {
        LicensePackage entity = findEntity(id);
        boolean nuevo = !entity.isDestacado();
        if (nuevo) {
            clearDestacadoExcept(id);
        }
        entity.setDestacado(nuevo);
        return toResponse(repository.save(entity));
    }

    @Override
    public List<LicensePackageResponse> resetDefaults() {
        List<LicensePackageResponse> restored = new ArrayList<>();
        for (LicensePackage seed : LicensePackageSeed.defaults()) {
            LicensePackage entity = repository.findById(seed.getId()).orElse(seed);
            copySeedValues(seed, entity);
            restored.add(toResponse(repository.save(entity)));
        }
        return restored;
    }

    private LicensePackage findEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paquete de licencia no encontrado: " + id));
    }

    private void clearDestacadoExcept(String id) {
        repository.findAllByOrderByIdAsc().stream()
                .filter(pkg -> !id.equals(pkg.getId()) && pkg.isDestacado())
                .forEach(pkg -> {
                    pkg.setDestacado(false);
                    repository.save(pkg);
                });
    }

    private void applyUpdate(LicensePackage entity, UpdateLicensePackageRequest request) {
        entity.setNombre(request.getNombre().trim());
        entity.setEtiqueta(trimOrEmpty(request.getEtiqueta()));
        entity.setDescripcion(request.getDescripcion().trim());
        entity.setMoneda(request.getMoneda().trim().toUpperCase());
        entity.setPricingModel(request.getPricingModel());
        entity.setPrecioPersonalizado(request.isPrecioPersonalizado());
        entity.setPrecioMensual(request.getPrecioMensual());
        entity.setPrecioAnual(request.getPrecioAnual());
        entity.setPrecioImplementacion(request.getPrecioImplementacion());
        entity.setMinUsuarios(request.getMinUsuarios());
        entity.setMaxUsuarios(request.getMaxUsuarios());
        entity.setMinSucursales(request.getMinSucursales());
        entity.setMaxSucursales(request.getMaxSucursales());
        entity.setSoporte(trimOrEmpty(request.getSoporte()));
        entity.setFunciones(request.getFunciones().stream().map(this::toFeature).toList());
        if (request.getFeatureCodes() != null) {
            entity.setFeatureCodes(request.getFeatureCodes());
        }
        entity.setAccent(request.getAccent());
        entity.setDestacado(request.isDestacado());
        entity.setActivo(request.isActivo());
    }

    private void copySeedValues(LicensePackage seed, LicensePackage entity) {
        entity.setId(seed.getId());
        entity.setNombre(seed.getNombre());
        entity.setEtiqueta(seed.getEtiqueta());
        entity.setDescripcion(seed.getDescripcion());
        entity.setMoneda(seed.getMoneda());
        entity.setPricingModel(seed.getPricingModel());
        entity.setPrecioMensual(seed.getPrecioMensual());
        entity.setPrecioAnual(seed.getPrecioAnual());
        entity.setPrecioImplementacion(seed.getPrecioImplementacion());
        entity.setPrecioPersonalizado(seed.isPrecioPersonalizado());
        entity.setMinUsuarios(seed.getMinUsuarios());
        entity.setMaxUsuarios(seed.getMaxUsuarios());
        entity.setMinSucursales(seed.getMinSucursales());
        entity.setMaxSucursales(seed.getMaxSucursales());
        entity.setSoporte(seed.getSoporte());
        entity.setFunciones(seed.getFunciones().stream()
                .map(f -> LicenseFeature.builder().label(f.getLabel()).incluido(f.isIncluido()).build())
                .toList());
        entity.setFeatureCodes(seed.getFeatureCodes());
        entity.setAccent(seed.getAccent());
        entity.setDestacado(seed.isDestacado());
        entity.setActivo(seed.isActivo());
    }

    private LicenseFeature toFeature(LicenseFeatureDto dto) {
        return LicenseFeature.builder()
                .label(dto.getLabel().trim())
                .incluido(dto.isIncluido())
                .build();
    }

    private LicensePackageResponse toResponse(LicensePackage entity) {
        return LicensePackageResponse.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .etiqueta(entity.getEtiqueta())
                .descripcion(entity.getDescripcion())
                .moneda(entity.getMoneda())
                .pricingModel(entity.getPricingModel())
                .precioMensual(entity.getPrecioMensual())
                .precioAnual(entity.getPrecioAnual())
                .precioImplementacion(entity.getPrecioImplementacion())
                .precioPersonalizado(entity.isPrecioPersonalizado())
                .minUsuarios(entity.getMinUsuarios())
                .maxUsuarios(entity.getMaxUsuarios())
                .minSucursales(entity.getMinSucursales())
                .maxSucursales(entity.getMaxSucursales())
                .soporte(entity.getSoporte())
                .funciones(entity.getFunciones() == null ? List.of() : entity.getFunciones().stream()
                        .map(f -> LicenseFeatureDto.builder()
                                .label(f.getLabel())
                                .incluido(f.isIncluido())
                                .build())
                        .toList())
                .featureCodes(entity.getFeatureCodes() == null ? List.of() : entity.getFeatureCodes())
                .accent(entity.getAccent())
                .destacado(entity.isDestacado())
                .activo(entity.isActivo())
                .build();
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
