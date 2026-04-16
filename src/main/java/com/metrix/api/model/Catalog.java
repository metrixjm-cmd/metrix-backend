package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Catálogo dinámico para gestionar valores como Puestos, Categorías, etc.
 * <p>
 * Cada entrada pertenece a un {@code type} (ej. "PUESTO", "CATEGORIA_TAREA")
 * y tiene un {@code value} (ej. "Cajero", "Limpieza profunda").
 * <p>
 * Los catálogos alimentan los dropdowns del frontend para evitar
 * que los usuarios inventen datos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "catalogs")
@CompoundIndex(name = "type_value_unique", def = "{'type': 1, 'value': 1}", unique = true)
public class Catalog {

    @Id
    private String id;

    /** Tipo de catálogo: PUESTO, CATEGORIA_TAREA, TURNO, etc. */
    @Field("type")
    private String type;

    /** Valor del catálogo (ej. "Cajero", "MATUTINO") */
    @Field("value")
    private String value;

    /** Etiqueta para mostrar en UI (opcional, si difiere del value) */
    @Field("label")
    private String label;

    /** Perfil de aplicacion al que pertenece el puesto (solo para type = PUESTO). */
    @Field("role")
    private Role role;

    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
