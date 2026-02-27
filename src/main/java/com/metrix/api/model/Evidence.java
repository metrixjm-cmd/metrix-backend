package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-documento de evidencias multimedia de ejecución (Obj. #13).
 * <p>
 * Almacena URLs de Google Cloud Storage generadas tras el upload al bucket
 * {@code metrix-evidences}. Las listas se inicializan vacías para soportar
 * el append progresivo de archivos durante la ejecución de la tarea.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {

    /** URLs de imágenes en GCS. Formato: gs://metrix-evidences/{taskId}/img/{filename} */
    @Builder.Default
    @Field("images")
    private List<String> images = new ArrayList<>();

    /** URLs de videos en GCS. Formato: gs://metrix-evidences/{taskId}/vid/{filename} */
    @Builder.Default
    @Field("videos")
    private List<String> videos = new ArrayList<>();
}
