package com.metrix.api.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

/**
 * Servicio de acceso a Google Cloud Storage para METRIX (Sprint 5).
 * <p>
 * Encapsula la lógica de upload de evidencias multimedia al bucket {@code metrix-evidences}.
 * <p>
 * Path de objetos en GCS: {@code {storeId}/{taskId}/{tipo}/{uuid}.{extension}}
 * donde tipo es {@code img} o {@code vid}.
 * <p>
 * Si el archivo de credenciales no existe (entorno de desarrollo sin GCS configurado),
 * el cliente se inicializa como {@code null} y las llamadas a {@link #uploadFile} lanzan
 * {@link IllegalStateException} con un mensaje descriptivo.
 */
@Slf4j
@Service
public class GcsService {

    @Value("${metrix.google-cloud.bucket-name}")
    private String bucketName;

    @Value("${metrix.google-cloud.credentials-path}")
    private Resource credentialsResource;

    private Storage storage;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsResource.getInputStream())
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");

            storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();

            log.info("GCS inicializado correctamente — bucket: {}", bucketName);

        } catch (IOException e) {
            log.warn("Credenciales GCS no encontradas ({}). " +
                     "El upload de evidencias estará deshabilitado hasta configurar " +
                     "src/main/resources/gcp-service-account.json", e.getMessage());
        }
    }

    /**
     * Sube un archivo al bucket de evidencias y retorna su URL pública.
     *
     * @param storeId     ID de la sucursal (primer segmento del path en GCS)
     * @param taskId      MongoDB _id de la tarea
     * @param tipo        "img" para imágenes, "vid" para videos
     * @param bytes       contenido binario del archivo
     * @param contentType MIME type (ej. image/jpeg, video/mp4)
     * @param extension   extensión del archivo sin punto (ej. jpg, mp4)
     * @return URL HTTPS del objeto subido
     * @throws IllegalStateException si el cliente GCS no está inicializado
     */
    public String uploadFile(String storeId, String taskId, String tipo,
                             byte[] bytes, String contentType, String extension) {
        if (storage == null) {
            throw new IllegalStateException(
                    "GCS no está configurado. Coloque gcp-service-account.json en " +
                    "backend-api/src/main/resources/ y reinicie el servidor.");
        }

        String blobName = storeId + "/" + taskId + "/" + tipo + "/" +
                          UUID.randomUUID() + "." + extension;

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName))
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, bytes);

        log.info("Evidencia subida a GCS: {}/{}", bucketName, blobName);

        return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
    }
}
