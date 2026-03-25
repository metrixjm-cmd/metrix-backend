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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * se usa almacenamiento local en disco como fallback.
 */
@Slf4j
@Service
public class GcsService {

    @Value("${metrix.google-cloud.bucket-name}")
    private String bucketName;

    @Value("${metrix.google-cloud.credentials-path}")
    private Resource credentialsResource;

    @Value("${server.port:8080}")
    private int serverPort;

    private Storage storage;
    private Path localStoragePath;

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
            log.warn("Credenciales GCS no encontradas. Usando almacenamiento local como fallback.");
            localStoragePath = Paths.get("uploads", "evidences");
            try {
                Files.createDirectories(localStoragePath);
                log.info("Almacenamiento local configurado en: {}", localStoragePath.toAbsolutePath());
            } catch (IOException ex) {
                log.error("No se pudo crear directorio de almacenamiento local", ex);
            }
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
     *
     * @param storeId     ID de la sucursal o "global" para materiales compartidos
     * @param entityId    ID temporal usado para evitar colisiones en el path
     * @param tipo        "pdf", "video" o "image"
     * @param bytes       contenido binario del archivo
     * @param contentType MIME type
     * @param extension   extensión sin punto (ej. pdf, mp4, jpg)
     * @return URL del material subido
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
            Path filePath = localStoragePath.resolve(relativePath);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
            log.info("Evidencia guardada localmente: {}", filePath);
            return "http://localhost:" + serverPort + "/api/v1/evidence/local/" + relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar evidencia en almacenamiento local.", e);
        }
    }
}
