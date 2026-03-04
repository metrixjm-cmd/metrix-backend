package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Entidad Sucursal (Store) — Sprint 11.
 * <p>
 * Cierra la deuda arquitectónica donde {@code storeId} era un String libre
 * en {@link User}, {@link Task} y {@link Training}.
 * <p>
 * El campo {@link #codigo} es único e inmutable (no se puede cambiar una vez creado).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stores")
public class Store {

    @Id
    private String id;

    @Field("nombre")
    private String nombre;

    /** Código único de la sucursal, p. ej. "STORE-CEN-001". Inmutable tras creación. */
    @Indexed(unique = true)
    @Field("codigo")
    private String codigo;

    @Field("direccion")
    private String direccion;

    @Field("telefono")
    private String telefono;

    /** Turnos operativos de la sucursal. Por defecto los tres estándar. */
    @Builder.Default
    @Field("turnos")
    private List<String> turnos = List.of("MATUTINO", "VESPERTINO", "NOCTURNO");

    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @Field("created_by")
    private String createdBy;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
