package com.atina.invoice.api.service;

import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.enums.StorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio para almacenamiento de archivos
 * Soporta almacenamiento local y S3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /**
     * Guardar attachment en storage
     * 
     * @param tenant Tenant propietario
     * @param senderId ID del sender (ej: "92455890")
     * @param emailId ID del email procesado
     * @param sequence Número de secuencia del attachment
     * @param source Tipo de documento (invoice, check, etc.)
     * @param destination Sistema destino (jde, sap, etc.)
     * @param originalFilename Nombre original del archivo
     * @param inputStream Stream del archivo
     * @return Path del archivo guardado
     */
    public String saveAttachment(
            Tenant tenant,
            String senderId,
            Long emailId,
            int sequence,
            String source,
            String destination,
            String originalFilename,
            InputStream inputStream) throws IOException {

        // Generar nombre normalizado
        String normalizedFilename = generateNormalizedFilename(
                senderId, emailId, sequence, source, destination, originalFilename);

        // Determinar storage type
        StorageType storageType = tenant.getStorageType() != null ? 
                tenant.getStorageType() : StorageType.LOCAL;

        String filePath;

        switch (storageType) {
            case S3:
                filePath = saveToS3(tenant, normalizedFilename, inputStream);
                break;

            case BOTH:
                // Guardar en local primero
                String localPath = saveToLocal(tenant, normalizedFilename, inputStream);
                // TODO: Async upload to S3
                filePath = localPath;
                break;

            case LOCAL:
            default:
                filePath = saveToLocal(tenant, normalizedFilename, inputStream);
                break;
        }

        log.info("Saved attachment: {} at {}", normalizedFilename, filePath);
        return filePath;
    }

    /**
     * Guardar metadata JSON del email
     * 
     * @param tenant Tenant propietario
     * @param senderId ID del sender
     * @param emailId ID del email
     * @param senderEmail Email del sender
     * @param jsonContent Contenido JSON
     * @return Path del archivo guardado
     */
    public String saveEmailMetadata(
            Tenant tenant,
            String senderId,
            Long emailId,
            String senderEmail,
            String jsonContent) throws IOException {

        // Generar nombre del archivo metadata
        String filename = String.format("%s_%d_%s.json", senderId, emailId, senderEmail);

        // Path del metadata
        String metadataPath = tenant.getStorageBasePath() != null ?
                tenant.getStorageBasePath() : "/private/tmp/process-mails";
        String fullPath = String.format("%s/%s/process/emails/%s",
                metadataPath, tenant.getTenantCode(), filename);

        // Crear directorios si no existen
        Path path = Paths.get(fullPath);
        Files.createDirectories(path.getParent());

        // Escribir JSON
        Files.writeString(path, jsonContent);

        log.info("Saved email metadata: {}", fullPath);
        return fullPath;
    }

    /**
     * Guardar archivo en almacenamiento local
     */
    private String saveToLocal(Tenant tenant, String normalizedFilename, InputStream inputStream) 
            throws IOException {

        // Base path
        String basePath = tenant.getStorageBasePath() != null ?
                tenant.getStorageBasePath() : "/private/tmp/process-mails";

        // Path completo: {basePath}/{TENANT_CODE}/process/inbounds/{filename}
        String fullPath = String.format("%s/%s/process/inbounds/%s",
                basePath, tenant.getTenantCode(), normalizedFilename);

        // Crear directorios si no existen
        Path path = Paths.get(fullPath);
        Files.createDirectories(path.getParent());

        // Guardar archivo
        try (FileOutputStream outputStream = new FileOutputStream(fullPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return fullPath;
    }

    /**
     * Guardar archivo en S3
     * TODO: Implementar cuando se necesite S3
     */
    private String saveToS3(Tenant tenant, String normalizedFilename, InputStream inputStream) 
            throws IOException {

        // TODO: Implementar S3 upload usando AWS SDK
        // Por ahora, guardar en local como fallback
        log.warn("S3 storage not implemented yet, using local storage");
        return saveToLocal(tenant, normalizedFilename, inputStream);

        /*
        // Ejemplo de implementación con AWS SDK:
        
        String bucketName = tenant.getS3BucketName();
        String key = String.format("%s/process/inbounds/%s", 
                tenant.getTenantCode(), normalizedFilename);

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(tenant.getS3Region())
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(
                                tenant.getS3AccessKey(), 
                                tenant.getS3SecretKey())))
                .build();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(inputStream.available());

        s3Client.putObject(bucketName, key, inputStream, metadata);

        return String.format("s3://%s/%s", bucketName, key);
        */
    }

    /**
     * Generar nombre normalizado del archivo
     * Formato: {senderId}_{emailId}_{sequence}_{source}_{destination}_{timestamp}.ext
     * Ejemplo: 92455890_3_0001_invoice_jde_2026-01-19-18-45-00.pdf
     */
    private String generateNormalizedFilename(
            String senderId,
            Long emailId,
            int sequence,
            String source,
            String destination,
            String originalFilename) {

        // Obtener extensión del archivo original
        String extension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalFilename.substring(lastDot);
        }

        // Generar timestamp
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);

        // Formato: {senderId}_{emailId}_{sequence}_{source}_{destination}_{timestamp}.ext
        return String.format("%s_%d_%04d_%s_%s_%s%s",
                senderId, emailId, sequence, source, destination, timestamp, extension);
    }

    /**
     * Verificar si un archivo existe
     */
    public boolean fileExists(String filePath) {
        if (filePath.startsWith("s3://")) {
            // TODO: Verificar en S3
            return false;
        } else {
            return Files.exists(Paths.get(filePath));
        }
    }

    /**
     * Obtener tamaño del archivo
     */
    public long getFileSize(String filePath) throws IOException {
        if (filePath.startsWith("s3://")) {
            // TODO: Obtener tamaño de S3
            return 0;
        } else {
            return Files.size(Paths.get(filePath));
        }
    }

    /**
     * Eliminar archivo
     */
    public void deleteFile(String filePath) throws IOException {
        if (filePath.startsWith("s3://")) {
            // TODO: Eliminar de S3
            log.warn("S3 delete not implemented yet");
        } else {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("Deleted file: {}", filePath);
        }
    }
}
