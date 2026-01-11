package com.atina.invoice.api.exception;

/**
 * Exception thrown when Docling conversion fails
 */
public class DoclingException extends RuntimeException {

    public DoclingException(String message) {
        super(message);
    }

    public DoclingException(String message, Throwable cause) {
        super(message, cause);
    }
}