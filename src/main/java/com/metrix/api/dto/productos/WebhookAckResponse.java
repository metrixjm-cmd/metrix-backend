package com.metrix.api.dto.productos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WebhookAckResponse {

    private boolean received;
    private String orderId;
    private boolean applied;
}
