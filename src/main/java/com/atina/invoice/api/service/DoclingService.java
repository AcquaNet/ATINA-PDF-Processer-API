package com.atina.invoice.api.service;

import com.atina.invoice.api.exception.DoclingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Docling service
 * Converts PDF documents to Docling JSON format
 * Supports both API mode and CLI mode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoclingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${docling.mode:api}")
    private String mode;

    @Value("${docling.api.url:http://localhost:5000}")
    private String apiUrl;

    @Value("${docling.api.timeout:60000}")
    private long apiTimeout;

    @Value("${docling.cli.path:/usr/local/bin/docling}")
    private String cliPath;

    @Value("${docling.temp.dir:${java.io.tmpdir}/docling}")
    private String tempDir;

    /**
     * Convert PDF to Docling JSON
     */
    public JsonNode convertPdf(MultipartFile file) throws DoclingException {
        log.info("Converting PDF to Docling JSON: {} (size: {} bytes, mode: {})",
                file.getOriginalFilename(), file.getSize(), mode);

        validatePdfFile(file);

        try {
            if ("api".equalsIgnoreCase(mode)) {
                return convertPdfViaApi(file);
            } else if ("cli".equalsIgnoreCase(mode)) {
                return convertPdfViaCli(file);
            } else {
                throw new DoclingException("Invalid Docling mode: " + mode);
            }
        } catch (DoclingException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF conversion failed", e);
            throw new DoclingException("PDF conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF via Docling API
     */
    private JsonNode convertPdfViaApi(MultipartFile file) throws IOException {
        log.debug("Using Docling API: {}", apiUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            String endpoint = apiUrl + "/convert";
            log.debug("Calling Docling API: POST {}", endpoint);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String jsonResponse = response.getBody();
                log.debug("Docling API response: {} chars", jsonResponse != null ? jsonResponse.length() : 0);
                return objectMapper.readTree(jsonResponse);
            } else {
                throw new DoclingException("Docling API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Docling API failed", e);
            throw new DoclingException("Docling API failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF via Docling CLI
     */
    private JsonNode convertPdfViaCli(MultipartFile file) throws IOException {
        log.debug("Using Docling CLI: {}", cliPath);

        Path tempDirPath = Path.of(tempDir);
        Files.createDirectories(tempDirPath);

        String tempFilename = "docling_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path tempPdfPath = tempDirPath.resolve(tempFilename);

        try {
            Files.copy(file.getInputStream(), tempPdfPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved temp PDF: {}", tempPdfPath);

            return executeCli(tempPdfPath);

        } finally {
            try {
                Files.deleteIfExists(tempPdfPath);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", tempPdfPath, e);
            }
        }
    }

    /**
     * Execute Docling CLI
     */
    private JsonNode executeCli(Path pdfPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                cliPath,
                pdfPath.toString(),
                "--output", "json"
        );

        processBuilder.redirectErrorStream(true);

        log.debug("Executing: {}", processBuilder.command());

        try {
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new DoclingException("Docling CLI failed: exit code " + exitCode);
            }

            return objectMapper.readTree(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DoclingException("Docling CLI interrupted", e);
        }
    }

    /**
     * Validate PDF file
     */
    private void validatePdfFile(MultipartFile file) throws DoclingException {
        if (file.isEmpty()) {
            throw new DoclingException("PDF file is empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new DoclingException("PDF too large: " + file.getSize() + " bytes (max 10MB)");
        }

        log.debug("PDF validated: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
    }
}
