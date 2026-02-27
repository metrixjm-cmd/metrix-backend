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
import java.util.Set;

/**
 * Entidad principal de usuario para METRIX.
 * <p>
 * Mapea directamente los campos definidos en la Gestión de Perfiles (Obj. #3):
 * Nombre / Puesto / Tienda / Turno / #Usuario.
 * <p>
 * Decisiones de diseño:
 * - {@code numeroUsuario} es único e indexado → lookup O(1) para login y reportes.
 * - {@code roles} es un Set para soportar Multi-Administración (Obj. #6): un GERENTE
 *   puede tener también rol ADMIN sin duplicar documentos.
 * - {@code storeId} referencia a la colección de Stores (futuro Sprint) para
 *   habilitar el Ranking Inter-Sucursal (KPI #6).
 * - {@code turno} usa String en lugar de Enum para soportar la Gestión de Turnos
 *   dinámica (Obj. #15): Matutino/Vespertino/Nocturno + turnos custom.
 * - Auditoría temporal con {@code createdAt} / {@code updatedAt} para trazabilidad.
 * - {@code activo} permite soft-delete sin perder históricos de KPIs (Obj. #11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    // ── Gestión de Perfiles (Obj. #3) ──────────────────────────────────

    /** Nombre completo del colaborador */
    @Field("nombre")
    private String nombre;

    /** Puesto operativo (e.g. "Cajero", "Supervisor de piso") */
    @Field("puesto")
    private String puesto;

    /**
     * Referencia a la unidad de trabajo / sucursal.
     * Se almacena como ID para facilitar el join con la colección "stores".
     */
    @Indexed
    @Field("store_id")
    private String storeId;

    /**
     * Turno asignado: MATUTINO | VESPERTINO | NOCTURNO.
     * String para flexibilidad con turnos custom (Obj. #15).
     */
    @Field("turno")
    private String turno;

    /** Identificador único de operador (#Usuario). Usado como username de login. */
    @Indexed(unique = true)
    @Field("numero_usuario")
    private String numeroUsuario;

    // ── Seguridad (Spring Security + JWT) ──────────────────────────────

    /** Password hasheado con BCrypt */
    @Field("password")
    private String password;

    /** Roles asignados. Set para multi-rol (Obj. #6). */
    @Field("roles")
    private Set<Role> roles;

    // ── Estado y Auditoría ─────────────────────────────────────────────

    /** Soft-delete flag. Preserva datos históricos para KPIs (Obj. #11). */
    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
