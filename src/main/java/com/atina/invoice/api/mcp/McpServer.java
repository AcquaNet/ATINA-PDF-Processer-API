package com.atina.invoice.api.mcp;

import com.atina.invoice.api.mcp.tool.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server
 * Implements Model Context Protocol for tool invocation
 * Exposes extraction and template generation capabilities to LLM clients
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServer {

    private final List<McpTool> tools;
    private final ObjectMapper objectMapper;

    /**
     * MCP Initialize
     * Returns server information and capabilities
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initialize(@RequestBody Map<String, Object> request) {
        log.info("MCP Initialize called");

        Map<String, Object> response = new HashMap<>();
        response.put("protocolVersion", "0.1.0");
        response.put("serverInfo", Map.of(
                "name", "invoice-extractor-mcp",
                "version", "1.0.0"
        ));
        response.put("capabilities", Map.of(
                "tools", Map.of()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * List available tools
     * Returns all tools with their schemas
     */
    @PostMapping("/tools/list")
    @GetMapping("/tools/list")
    public ResponseEntity<Map<String, Object>> listTools() {
        log.info("MCP List Tools called");

        ArrayNode toolsArray = objectMapper.createArrayNode();

        for (McpTool tool : tools) {
            if (tool.isAvailable()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.getName());
                toolNode.put("description", tool.getDescription());
                toolNode.set("inputSchema", tool.getInputSchema());

                toolsArray.add(toolNode);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tools", toolsArray);

        log.info("Listed {} available tools", toolsArray.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Call a tool
     * Executes the specified tool with given arguments
     */
    @PostMapping("/tools/call")
    public ResponseEntity<Map<String, Object>> callTool(@RequestBody Map<String, Object> request) {
        try {
            String toolName = (String) request.get("name");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");

            log.info("MCP Tool Call: {} with {} arguments", toolName, 
                     arguments != null ? arguments.size() : 0);

            // Find tool
            McpTool tool = tools.stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElse(null);

            if (tool == null) {
                log.error("Tool not found: {}", toolName);
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", "Tool not found: " + toolName,
                                "available_tools", tools.stream()
                                        .map(McpTool::getName)
                                        .toList()
                        ));
            }

            if (!tool.isAvailable()) {
                log.error("Tool not available: {}", toolName);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tool not available: " + toolName));
            }

            // Execute tool
            Map<String, Object> result = tool.execute(arguments != null ? arguments : new HashMap<>());

            // Build MCP response
            Map<String, Object> response = new HashMap<>();
            response.put("content", List.of(Map.of(
                    "type", "text",
                    "text", objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(result)
            )));

            log.info("Tool '{}' executed successfully", toolName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Tool execution failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("type", e.getClass().getSimpleName());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("mcp_version", "0.1.0");
        health.put("available_tools", tools.stream()
                .filter(McpTool::isAvailable)
                .map(McpTool::getName)
                .toList());

        return ResponseEntity.ok(health);
    }

    /**
     * Get server info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("server_name", "invoice-extractor-mcp");
        info.put("version", "1.0.0");
        info.put("protocol_version", "0.1.0");
        info.put("description", "MCP server for invoice data extraction and template generation using AI");
        info.put("tools_count", tools.size());
        info.put("tools", tools.stream()
                .map(t -> Map.of(
                        "name", t.getName(),
                        "description", t.getDescription(),
                        "available", t.isAvailable()
                ))
                .toList());

        return ResponseEntity.ok(info);
    }
}
