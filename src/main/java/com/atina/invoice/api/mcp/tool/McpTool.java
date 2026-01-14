package com.atina.invoice.api.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * MCP Tool Interface
 * Defines contract for tools exposed via Model Context Protocol
 */
public interface McpTool {

    /**
     * Get tool name (unique identifier)
     */
    String getName();

    /**
     * Get tool description for LLM
     */
    String getDescription();

    /**
     * Get tool input schema (JSON Schema format)
     */
    JsonNode getInputSchema();

    /**
     * Execute tool with given arguments
     * 
     * @param arguments Tool arguments from LLM
     * @return Tool result
     */
    Map<String, Object> execute(Map<String, Object> arguments) throws Exception;

    /**
     * Check if tool is available
     */
    default boolean isAvailable() {
        return true;
    }
}
