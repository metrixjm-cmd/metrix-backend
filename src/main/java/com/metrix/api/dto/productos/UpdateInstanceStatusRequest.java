package com.metrix.api.dto.productos;

import com.metrix.api.platform.model.MetrixInstanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInstanceStatusRequest {

    @NotNull
    private MetrixInstanceStatus status;
}
