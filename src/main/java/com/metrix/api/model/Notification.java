package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Notificación persistida de un usuario. Se crea junto con cada envío de
 * {@link com.metrix.api.dto.NotificationEvent}, independientemente de si el
 * usuario tenía una conexión SSE activa en ese momento — así se puede
 * consultar el historial al iniciar sesión aunque se haya perdido la
 * entrega en tiempo real.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "idx_notif_user", def = "{'user_id': 1, 'created_at': -1}")
})
public class Notification {

    @Id
    private String id;

    /**
     * Id del {@code NotificationEvent} que originó esta notificación.
     * <p>
     * Un mismo evento se entrega a varios destinatarios (al ejecutor y a su gerente,
     * o a todos los ADMIN), y cada uno necesita su propio documento. Antes el id del
     * evento se usaba como {@code _id}, así que el segundo destinatario sobrescribía
     * al primero y sólo el último conservaba la notificación en su historial.
     * <p>
     * No lo consume el cliente — deduplica por {@code _id}, que es el mismo en vivo
     * y en el historial. Se guarda porque es lo único que permite reconstruir a
     * quiénes alcanzó un evento, que es justo lo que hacía falta para diagnosticar
     * la pérdida anterior.
     */
    @Field("event_id")
    private String eventId;

    @Field("user_id")
    private String userId;

    @Field("type")
    private String type;

    @Field("severity")
    private String severity;

    @Field("title")
    private String title;

    @Field("body")
    private String body;

    @Field("task_id")
    private String taskId;

    @Field("incident_id")
    private String incidentId;

    @Field("exam_id")
    private String examId;

    @Field("store_id")
    private String storeId;

    @Builder.Default
    @Field("read")
    private boolean read = false;

    @Field("created_at")
    private Instant createdAt;
}
