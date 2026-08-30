package com.metrix.api.dto.productos;

import com.metrix.api.platform.model.MetrixInstanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MetrixInstanceResponse {

    private String id;
    private String databaseName;
    private String empresaNombre;
    private String licensePackageId;
    private String licensePackageNombre;
    private String orderId;
    private String adminNumeroUsuario;
    private String adminNombre;
    private String contactoEmail;
    private MetrixInstanceStatus status;
    private Instant createdAt;
}
