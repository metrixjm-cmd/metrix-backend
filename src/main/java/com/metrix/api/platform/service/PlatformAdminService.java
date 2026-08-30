package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.MetrixInstanceResponse;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final MetrixInstanceRepository instanceRepository;

    public List<MetrixInstanceResponse> listInstances() {
        return instanceRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private MetrixInstanceResponse toResponse(MetrixInstance instance) {
        return MetrixInstanceResponse.builder()
                .id(instance.getId())
                .databaseName(instance.getDatabaseName())
                .empresaNombre(instance.getEmpresaNombre())
                .licensePackageId(instance.getLicensePackageId())
                .licensePackageNombre(instance.getLicensePackageNombre())
                .orderId(instance.getOrderId())
                .adminNumeroUsuario(instance.getAdminNumeroUsuario())
                .adminNombre(instance.getAdminNombre())
                .contactoEmail(instance.getContactoEmail())
                .status(instance.getStatus())
                .createdAt(instance.getCreatedAt())
                .build();
    }
}
