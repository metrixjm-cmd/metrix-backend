package com.metrix.api.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Servicio de acceso a Google Cloud Storage para METRIX (Sprint 5).
 * <p>
 * Encapsula la lógica de upload de evidencias multimedia al bucket configurado.
 * <p>
 * Path de objetos en GCS: {@code {storeId}/{taskId}/{tipo}/{uuid}.{extension}}
 * donde tipo es {@code img} o {@code vid}.
 * <p>
 * En entornos locales puede usar almacenamiento en disco como fallback,
 * controlado por la propiedad {@code metrix.google-cloud.local-fallback-enabled}.
 */
@Slf4j
@Service
public class GcsService {

    @Value("${metrix.google-cloud.bucket-name}")
    private String bucketName;

    @Value("${metrix.google-cloud.project-id:}")
    private String projectId;

    @Value("${metrix.google-cloud.credentials-path:}")
    private String credentialsPath;

    @Value("${metrix.google-cloud.local-fallback-enabled:true}")
    private boolean localFallbackEnabled;

    @Value("${server.port:8080}")
    private int serverPort;

    private final ResourceLoader resourceLoader;

    private Storage storage;
    private Path localStoragePath;

    public GcsService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            validateBucketName();
            storage = createStorageClient();
            ensureBucketExistsAndReachable();

            String authMode = StringUtils.hasText(credentialsPath)
                    ? "service-account-json"
                    : "adc/workload-identity";
            log.info("GCS inicializado correctamente. bucket={}, projectId={}, auth={}",
                    bucketName,
                    StringUtils.hasText(projectId) ? projectId : "<auto>",
                    authMode);
        } catch (Exception e) {
            handleGcsInitializationFailure(e);
        }
    }

    /**
     * Sube un archivo al bucket de evidencias (GCS) o al filesystem local (fallback).
     * Usado para evidencias de tareas e incidencias.
     *
     * @param storeId     ID de la sucursal (primer segmento del path)
     * @param taskId      MongoDB _id de la tarea o incidencia
     * @param tipo        "img" para imágenes, "vid" para videos
     * @param bytes       contenido binario del archivo
     * @param contentType MIME type (ej. image/jpeg, video/mp4)
     * @param extension   extensión del archivo sin punto (ej. jpg, mp4)
     * @return URL del objeto subido
     */
    public String uploadFile(String storeId, String taskId, String tipo,
                             byte[] bytes, String contentType, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        String relativePath = storeId + "/" + taskId + "/" + tipo + "/" + fileName;

        if (storage != null) {
            return uploadToGcs(relativePath, bytes, contentType);
        }

        return uploadToLocal(relativePath, bytes);
    }

    /**
     * Sube un material del banco de información (PDF, video, imagen).
     * Path: {@code materials/{storeId}/{tipo}/{uuid}.{extension}}
     */
    public String uploadMaterial(String storeId, String entityId, String tipo,
                                 byte[] bytes, String contentType, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        String relativePath = "materials/" + storeId + "/" + tipo + "/" + fileName;

        if (storage != null) {
            return uploadToGcs(relativePath, bytes, contentType);
        }

        return uploadToLocal(relativePath, bytes);
    }

    private String uploadToGcs(String blobName, byte[] bytes, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName))
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, bytes);
        log.info("Evidencia subida a GCS: {}/{}", bucketName, blobName);
        return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
    }

    private String uploadToLocal(String relativePath, byte[] bytes) {
        try {
            if (localStoragePath == null) {
                throw new IllegalStateException("Fallback local no inicializado.");
            }
            Path filePath = localStoragePath.resolve(relativePath);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
            log.info("Evidencia guardada localmente: {}", filePath);
            return "http://localhost:" + serverPort + "/api/v1/evidence/local/" + relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar evidencia en almacenamiento local.", e);
        }
    }

    private void validateBucketName() {
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalStateException(
                    "Propiedad requerida faltante: metrix.google-cloud.bucket-name (GCS_BUCKET_NAME)."
            );
        }
    }

    private Storage createStorageClient() throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (StringUtils.hasText(projectId)) {
            builder.setProjectId(projectId);
        }

        if (StringUtils.hasText(credentialsPath)) {
            Resource resource = resourceLoader.getResource(credentialsPath);
            if (!resource.exists()) {
                throw new IOException("No se encontró archivo de credenciales GCS en: " + credentialsPath);
            }

            try (InputStream is = resource.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(is)
                        .createScoped("https://www.googleapis.com/auth/cloud-platform");
                builder.setCredentials(credentials);
            }
        }

        return builder.build().getService();
    }

    private void ensureBucketExistsAndReachable() {
        Bucket bucket = storage.get(bucketName);
        if (bucket == null) {
            throw new IllegalStateException("El bucket GCS no existe o no es accesible: " + bucketName);
        }
    }

    private void handleGcsInitializationFailure(Exception e) {
        if (!localFallbackEnabled) {
            throw new IllegalStateException(
                    "No se pudo inicializar GCS y el fallback local está deshabilitado. " +
                    "Verifica GCS_BUCKET_NAME, GCS_PROJECT_ID, GCS_CREDENTIALS_PATH o ADC/Workload Identity.",
                    e
            );
        }

        log.warn("No se pudo inicializar GCS. Se usará almacenamiento local de fallback. Causa: {}", e.getMessage());
        localStoragePath = Paths.get("uploads", "evidences");
        try {
            Files.createDirectories(localStoragePath);
            log.info("Almacenamiento local configurado en: {}", localStoragePath.toAbsolutePath());
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear directorio de almacenamiento local fallback.", ex);
        }
    }
}

