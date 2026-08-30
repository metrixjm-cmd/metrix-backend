package com.metrix.api.platform.model;

import com.metrix.api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Set;

/**
 * Admin 0 y operadores de plataforma — viven solo en {@code metrix_platform}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platform_users")
public class PlatformUser {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    @Field("numero_usuario")
    private String numeroUsuario;

    @Field("nombre")
    private String nombre;

    @Field("password")
    private String password;

    @Field("roles")
    private Set<Role> roles;

    @Builder.Default
    @Field("platform_admin")
    private boolean platformAdmin = true;

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
