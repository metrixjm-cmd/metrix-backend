package com.metrix.api.dto.productos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProvisionMetrixResponse {

    private String instanceId;
    private String databaseName;
    private String adminNumeroUsuario;
    private String loginUrl;
    private String message;
}
