package com.metrix.api.dto.productos;

import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.MetrixInstanceSuspensionReason;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

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
    private boolean onTrial;
    private Instant trialEndsAt;
    private MetrixInstanceSuspensionReason suspensionReason;
    private Instant createdAt;
    private Integer maxUsuarios;
    private Integer maxSucursales;
    private Integer sucursalesContratadas;
    private List<String> featureCodes;
    private Instant paidAt;
}
