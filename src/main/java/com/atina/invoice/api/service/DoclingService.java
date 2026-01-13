package com.atina.invoice.api.service;

import com.atina.invoice.api.exception.DoclingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Docling service
 * Converts PDF documents to Docling JSON format via API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoclingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${docling.host:docling.localhost}")
    private String doclingHost;

    @Value("${docling.port:8080}")
    private int doclingPort;

    @Value("${docling.user:__admin__}")
    private String doclingUser;

    @Value("${docling.pass:admin123}")
    private String doclingPass;

    /**
     * Convert PDF to Docling JSON
     */
    public JsonNode convertPdf(MultipartFile file) throws DoclingException {
        log.info("Converting PDF to Docling JSON: {} (size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        validatePdfFile(file);

        try {
            return convertPdfViaApi(file);
        } catch (DoclingException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF conversion failed", e);
            throw new DoclingException("PDF conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF via Docling API
     * Uses the same format as Mulesoft integration
     */
    private JsonNode convertPdfViaApi(MultipartFile file) throws IOException {
        String apiUrl = String.format("http://%s:%d", doclingHost, doclingPort);
        String endpoint = apiUrl + "/v1/convert/source";

        log.debug("Calling Docling API: POST {}", endpoint);

        // Build request body
        ObjectNode requestBody = buildDoclingRequest(file);

        log.debug("Request body size: {} bytes", requestBody.toString().length());

        // Build headers with Basic Auth
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(doclingUser, doclingPass);

        HttpEntity<ObjectNode> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String jsonResponse = response.getBody();
                log.debug("Docling API response: {} chars",
                        jsonResponse != null ? jsonResponse.length() : 0);

                // Parse response and extract document
                JsonNode responseJson = objectMapper.readTree(jsonResponse);

                // The response should contain the converted document
                // Extract the actual Docling JSON from the response
                return extractDoclingJson(responseJson);

            } else {
                throw new DoclingException("Docling API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Docling API call failed", e);
            throw new DoclingException("Docling API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build Docling API request body
     * Same format as Mulesoft integration
     */
    private ObjectNode buildDoclingRequest(MultipartFile file) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();

        // Options
        ObjectNode options = objectMapper.createObjectNode();

        ArrayNode fromFormats = objectMapper.createArrayNode();
        fromFormats.add("pdf");
        options.set("from_formats", fromFormats);

        ArrayNode toFormats = objectMapper.createArrayNode();
        toFormats.add("json");
        options.set("to_formats", toFormats);

        options.put("image_export_mode", "placeholder");
        options.put("do_ocr", false);
        options.put("force_ocr", false);
        options.put("pdf_backend", "dlparse_v4");
        options.put("table_mode", "accurate");
        options.put("table_cell_matching", true);
        options.put("document_timeout", 604800);
        options.put("abort_on_error", false);
        options.put("do_table_structure", true);
        options.put("include_images", false);
        options.put("images_scale", 2);

        request.set("options", options);

        // Sources - PDF as base64
        ArrayNode sources = objectMapper.createArrayNode();
        ObjectNode source = objectMapper.createObjectNode();
        source.put("kind", "file");
        source.put("filename", file.getOriginalFilename() != null ?
                file.getOriginalFilename() : "file.pdf");

        // Convert PDF to base64
        byte[] pdfBytes = file.getBytes();
        String base64String = Base64.getEncoder().encodeToString(pdfBytes);
        source.put("base64_string", base64String);

        sources.add(source);
        request.set("sources", sources);

        // Target
        ObjectNode target = objectMapper.createObjectNode();
        target.put("kind", "inbody");
        request.set("target", target);

        return request;
    }

    /**
     * Extract Docling JSON from API response
     */
    private JsonNode extractDoclingJson(JsonNode response) throws DoclingException {
        // The response structure may vary depending on Docling API version
        // Adjust this based on actual response format

        // Check if response has documents array
        if (response.has("documents") && response.get("documents").isArray()) {
            ArrayNode documents = (ArrayNode) response.get("documents");
            if (documents.size() > 0) {
                return documents.get(0);
            }
        }

        // Check if response has document field
        if (response.has("document")) {
            return response.get("document");
        }

        // If response is already the document, return as is
        if (response.has("schema_name") || response.has("name")) {
            return response;
        }

        log.warn("Unexpected Docling response structure. Returning full response.");
        return response;
    }

    /**
     * Validate PDF file
     */
    private void validatePdfFile(MultipartFile file) throws DoclingException {
        if (file.isEmpty()) {
            throw new DoclingException("PDF file is empty");
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            throw new DoclingException("PDF too large: " + file.getSize() +
                    " bytes (max 50MB)");
        }

        log.debug("PDF validated: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());
    }
}