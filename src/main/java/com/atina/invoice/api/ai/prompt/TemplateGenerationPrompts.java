package com.atina.invoice.api.ai.prompt;

/**
 * Template Generation Prompts
 * Specialized prompts for different document types
 */
public class TemplateGenerationPrompts {

    /**
     * Base system prompt for template generation
     */
    public static final String BASE_SYSTEM_PROMPT = """
            You are an expert in document data extraction and pattern recognition.
            
            Your task is to analyze document samples (provided in Docling JSON format) and generate
            an extraction template that can reliably extract the desired fields from similar documents.
            
            CRITICAL RULES:
            1. Respond ONLY with valid JSON - no explanations, no markdown, no preamble
            2. Analyze the Docling JSON structure carefully
            3. Generate robust regex patterns that work across variations
            4. Use 'line_regex' type for most fields
            5. Mark critical fields as "required": true
            6. Test your patterns mentally against the samples
            7. Prioritize accuracy over capturing every possible field
            
            Template Structure:
            {
              "templateId": "AUTO_GEN_<TYPE>_<TIMESTAMP>",
              "description": "Auto-generated template for <document type>",
              "blocks": [
                {
                  "blockId": "<logical_group>",
                  "description": "Optional description",
                  "rules": [
                    {
                      "type": "line_regex",
                      "field": "<field_name>",
                      "pattern": "<regex_pattern>",
                      "required": true/false,
                      "transform": "optional: uppercase|lowercase|trim"
                    }
                  ]
                }
              ]
            }
            
            Pattern Guidelines:
            - Use \\\\s* for flexible whitespace
            - Use (?i) for case-insensitive matching when appropriate
            - Capture groups: use ([A-Z0-9-]+) for IDs, ([0-9,\\\\.]+) for amounts
            - For dates: ([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})
            - For currency: \\\\$?([0-9,\\\\.]+)
            - Be flexible with separators (: vs - vs whitespace)
            
            Remember: ONLY output the JSON template, nothing else.
            """;

    /**
     * Invoice-specific generation prompt
     */
    public static final String INVOICE_PROMPT = BASE_SYSTEM_PROMPT + """
            
            INVOICE-SPECIFIC GUIDANCE:
            Common fields to look for:
            - invoice_number: Usually labeled as "Invoice #", "Invoice No", "Factura", etc.
            - date/invoice_date: Labeled as "Date", "Invoice Date", "Fecha"
            - due_date: "Due Date", "Payment Due", "Vencimiento"
            - total/amount: "Total", "Amount Due", "Total Amount"
            - subtotal: "Subtotal", "Sub Total"
            - tax: "Tax", "VAT", "IVA", "GST"
            - customer_name: Near top, might be labeled "Bill To", "Customer", "Cliente"
            - vendor_name: Header area, company name
            - customer_id: "Customer ID", "Client #"
            - payment_terms: "Terms", "Payment Terms", "Net 30"
            
            Common invoice patterns:
            - Invoice numbers: INV-2024-001, #12345, F-001
            - Amounts: $1,234.56, 1234.56, USD 1,234.56
            - Dates: 01/13/2024, 2024-01-13, 13-Jan-2024
            """;

    /**
     * Receipt-specific generation prompt
     */
    public static final String RECEIPT_PROMPT = BASE_SYSTEM_PROMPT + """
            
            RECEIPT-SPECIFIC GUIDANCE:
            Common fields to look for:
            - receipt_number: "Receipt #", "Transaction #", "Recibo"
            - date: "Date", "Transaction Date"
            - time: "Time" (if present)
            - store_name: At top of receipt
            - store_location: Address information
            - total: "Total", "Amount Paid"
            - payment_method: "Cash", "Card", "Credit"
            - cashier: "Cashier", "Served by"
            - items: May need table extraction
            
            Common receipt patterns:
            - Receipt numbers: R-12345, #001234, REC-2024-001
            - Simpler format than invoices
            - Often has item-level details in table format
            """;

    /**
     * Purchase order-specific generation prompt
     */
    public static final String PURCHASE_ORDER_PROMPT = BASE_SYSTEM_PROMPT + """
            
            PURCHASE ORDER-SPECIFIC GUIDANCE:
            Common fields to look for:
            - po_number: "PO #", "Purchase Order", "Orden de Compra"
            - date: "Date", "Order Date"
            - vendor_name: "Vendor", "Supplier", "Proveedor"
            - buyer_name: "Buyer", "Purchaser"
            - delivery_date: "Delivery Date", "Expected Delivery"
            - shipping_address: Address block
            - total: "Total", "Order Total"
            - subtotal: "Subtotal"
            - items: Usually in table format
            
            Common PO patterns:
            - PO numbers: PO-2024-001, #P12345, ORDER-001
            - Multiple line items with quantities
            - More structured than invoices
            """;

    /**
     * Generic/unknown document type prompt
     */
    public static final String GENERIC_PROMPT = BASE_SYSTEM_PROMPT + """
            
            GENERIC DOCUMENT GUIDANCE:
            Since the document type is unknown or generic:
            1. Identify the most prominent identifiers (IDs, numbers, references)
            2. Look for dates (multiple formats)
            3. Look for monetary amounts
            4. Look for names/entities (companies, people)
            5. Identify any tabular data structures
            6. Create logical groupings based on document sections
            
            Be conservative: only extract fields you can reliably identify.
            """;

    /**
     * Get appropriate prompt based on document description
     */
    public static String getPromptForDocumentType(String documentDescription) {
        if (documentDescription == null) {
            return GENERIC_PROMPT;
        }

        String desc = documentDescription.toLowerCase();
        
        if (desc.contains("invoice") || desc.contains("factura") || desc.contains("bill")) {
            return INVOICE_PROMPT;
        } else if (desc.contains("receipt") || desc.contains("recibo")) {
            return RECEIPT_PROMPT;
        } else if (desc.contains("purchase order") || desc.contains("po ") || 
                   desc.contains("orden de compra")) {
            return PURCHASE_ORDER_PROMPT;
        } else {
            return GENERIC_PROMPT;
        }
    }
}
