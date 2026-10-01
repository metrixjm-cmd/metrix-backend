package com.metrix.api.dto.productos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class UserPackStatusResponse {

    /** Hay precio y tamaño, y el plan contratado tiene cupo finito. */
    private boolean disponible;
    private String motivoNoDisponible;
    private int usuariosPorPaquete;
    private BigDecimal precio;
    private String moneda;
    /** Cupo del plan contratado. {@code null} = ilimitado. */
    private Integer maxUsuariosPlan;
    private int usuariosExtraVigentes;
    /** Plan + paquete vigente. {@code null} si el plan es ilimitado. */
    private Integer maxUsuariosEfectivo;
    private boolean vigente;
    private Instant vigenteHasta;
    /** Hubo un paquete y el periodo ya terminó. */
    private boolean vencido;
    private int periodoDias;
}
