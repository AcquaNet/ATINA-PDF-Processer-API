package com.atina.invoice.api.ai.prompt;

/**
 * IMPROVED Template Generation Prompts
 * Enhanced prompts with better instructions and examples
 */
public class TemplateGenerationPrompts {

    /**
     * Base system prompt for template generation - IMPROVED
     */
    public static final String BASE_SYSTEM_PROMPT = """
            You are an expert in document data extraction and regex pattern generation.
            
            Your task: Analyze document samples (in Docling JSON format) and generate a robust 
            extraction template that reliably extracts fields from similar documents.
            
            ═══════════════════════════════════════════════════════════════════════════════
            CRITICAL OUTPUT RULE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Respond with ONLY a valid JSON template. No explanations. No markdown. No preamble.
            Do NOT wrap the JSON in markdown fences like ```json ... ```
            Output ONLY the raw JSON starting with { and ending with }
            
            ═══════════════════════════════════════════════════════════════════════════════
            TEMPLATE STRUCTURE
            ═══════════════════════════════════════════════════════════════════════════════
            
            {
              "templateId": "AUTO_GEN_<DOCTYPE>_<TIMESTAMP>",
              "description": "Auto-generated template for <document type>",
              "blocks": [
                {
                  "blockId": "header",
                  "description": "Document identifiers and metadata",
                  "rules": [
                    {
                      "type": "line_regex",
                      "field": "invoice_number",
                      "pattern": "(?i)Invoice\\\\s*(?:#|No\\\\.?|Number)?[:\\\\s-]*(INV-?[0-9A-Z-]+|[0-9]{4,})",
                      "required": true,
                      "transform": "trim"
                    },
                    {
                      "type": "line_regex",
                      "field": "date",
                      "pattern": "(?i)(?:Invoice\\\\s+)?Date[:\\\\s-]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})",
                      "required": true,
                      "transform": "trim"
                    }
                  ]
                },
                {
                  "blockId": "amounts",
                  "description": "Financial amounts",
                  "rules": [
                    {
                      "type": "line_regex",
                      "field": "total",
                      "pattern": "(?i)Total[:\\\\s]*\\\\$?\\\\s*([0-9,]+\\\\.?[0-9]*)",
                      "required": true,
                      "transform": "trim"
                    }
                  ]
                }
              ]
            }
            
            ═══════════════════════════════════════════════════════════════════════════════
            UNDERSTANDING DOCLING JSON STRUCTURE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Docling provides documents in this structure:
            
            {
              "pages": [
                {
                  "page_no": 1,
                  "text": "Full text content of the page..."
                }
              ],
              "main_text": "Combined text from all pages"
            }
            
            Focus on:
            1. "main_text" - This contains all extractable text
            2. Look for patterns within this text
            3. The text follows the visual layout of the document
            
            ═══════════════════════════════════════════════════════════════════════════════
            REGEX PATTERN BUILDING RULES
            ═══════════════════════════════════════════════════════════════════════════════
            
            1. CASE INSENSITIVITY
               - Start patterns with (?i) for case-insensitive matching
               - Example: (?i)Invoice vs INVOICE vs invoice
            
            2. WHITESPACE FLEXIBILITY
               - Use \\\\s* for zero or more spaces
               - Use \\\\s+ for one or more spaces
               - Example: Invoice\\\\s*#\\\\s*12345
            
            3. LABEL VARIATIONS
               - Account for different separators: : - (nothing)
               - Example: (?i)Invoice\\\\s*(?:#|No\\\\.?|Number)?[:\\\\s-]*
               - This matches: "Invoice #", "Invoice No.", "Invoice:", "Invoice 12345"
            
            4. VALUE CAPTURE GROUPS
               - Place parentheses around the VALUE you want to extract
               - Example: Invoice[:\\\\s-]*(INV-[0-9]+) captures "INV-12345"
               - NOT: (Invoice[:\\\\s-]*INV-[0-9]+) - this captures the label too!
            
            5. COMMON PATTERNS BY DATA TYPE
            
               INVOICE/DOCUMENT NUMBERS:
               - Pattern: (INV-?[0-9A-Z]{3,}|[0-9]{4,}|[A-Z]{1,3}-[0-9]{3,})
               - Matches: INV-12345, INV12345, 001234, A-12345, FC-00123
            
               DATES:
               - Pattern: ([0-9]{1,2}[-/\\\\.][0-9]{1,2}[-/\\\\.][0-9]{2,4})
               - Matches: 01/13/2024, 13-01-24, 2024.01.13
            
               CURRENCY AMOUNTS:
               - Pattern: \\\\$?\\\\s*([0-9]{1,3}(?:,?[0-9]{3})*(?:\\\\.[0-9]{2})?)
               - Matches: $1,234.56, 1234.56, $1234, 1,234
            
               NAMES/TEXT (single line):
               - Pattern: ([A-ZÁÉÍÓÚÑa-záéíóúñ][A-ZÁÉÍÓÚÑa-záéíóúñ\\\\s\\\\.,-]{2,60})
               - Matches: John Doe, María García, ACME Corp.
            
               NAMES/TEXT (multi-word, uppercase):
               - Pattern: ([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ0-9\\\\s&,\\\\.'-]{3,80})
               - Matches: JOHN DOE, ACME CORP & CO., MARÍA DEL CARMEN
            
               EMAIL:
               - Pattern: ([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,})
            
               PHONE:
               - Pattern: (\\\\+?[0-9]{1,3}[\\\\s-]?\\\\(?[0-9]{2,4}\\\\)?[\\\\s-]?[0-9]{3,4}[\\\\s-]?[0-9]{3,4})
            
            6. ESCAPE CHARACTERS
               - In JSON strings, backslashes must be DOUBLED
               - \\\\s becomes \\\\\\\\s in JSON
               - \\\\. becomes \\\\\\\\.
               - Example pattern in JSON: "pattern": "Invoice\\\\\\\\s*#?\\\\\\\\s*([0-9]+)"
            
            7. OPTIONAL ELEMENTS
               - Use ? for optional parts
               - Example: Invoice\\\\s*#?\\\\s* (# is optional)
               - Example: (?:USD|\\\\$)?\\\\s* (currency symbol optional)
            
            8. ANCHORING
               - Avoid using ^ or $ unless absolutely necessary
               - Patterns match anywhere in a line
               - Focus on unique surrounding context
            
            ═══════════════════════════════════════════════════════════════════════════════
            BLOCK ORGANIZATION STRATEGY
            ═══════════════════════════════════════════════════════════════════════════════
            
            Organize rules into logical blocks:
            
            1. "header" block:
               - Document identifiers (invoice_number, po_number, etc.)
               - Document metadata (date, type, status)
            
            2. "parties" block:
               - Customer/client information (customer_name, customer_id)
               - Vendor/supplier information (vendor_name, vendor_id)
            
            3. "amounts" block:
               - Financial values (subtotal, tax, total, amount_due)
            
            4. "dates" block (if many dates):
               - issue_date, due_date, delivery_date, etc.
            
            5. "additional" block:
               - Any other fields (payment_terms, notes, references)
            
            ═══════════════════════════════════════════════════════════════════════════════
            FIELD NAMING CONVENTIONS
            ═══════════════════════════════════════════════════════════════════════════════
            
            Use snake_case for field names:
            - invoice_number (not invoiceNumber or InvoiceNumber)
            - customer_name (not customerName)
            - total_amount (not totalAmount)
            
            Common field names:
            - invoice_number, receipt_number, po_number
            - issue_date, due_date, delivery_date
            - customer_name, customer_id, customer_address
            - vendor_name, vendor_id, vendor_address
            - subtotal, tax, tax_amount, total, amount_due
            - payment_terms, payment_method
            - currency, exchange_rate
            
            ═══════════════════════════════════════════════════════════════════════════════
            REQUIRED vs OPTIONAL FIELDS
            ═══════════════════════════════════════════════════════════════════════════════
            
            Mark as "required": true for:
            - Document identifiers (invoice_number, etc.)
            - Critical amounts (total, amount_due)
            - Essential dates (issue_date, due_date)
            - Key parties (customer_name or customer_id)
            
            Mark as "required": false for:
            - Optional metadata (notes, references)
            - Secondary amounts (subtotal, tax if total is captured)
            - Additional contact info (phone, email if not critical)
            
            ═══════════════════════════════════════════════════════════════════════════════
            TRANSFORMS
            ═══════════════════════════════════════════════════════════════════════════════
            
            Available transforms (can chain with |):
            - "trim" - Remove leading/trailing whitespace
            - "uppercase" - Convert to uppercase
            - "lowercase" - Convert to lowercase
            
            Usage:
            - "transform": "trim" (single)
            - "transform": "uppercase|trim" (chain)
            
            When to use:
            - Always use "trim" for all fields
            - Use "uppercase" for IDs and codes (invoice_number, customer_id)
            - Use "lowercase" for emails
            
            ═══════════════════════════════════════════════════════════════════════════════
            EXAMPLE: GOOD vs BAD PATTERNS
            ═══════════════════════════════════════════════════════════════════════════════
            
            BAD: "pattern": "Invoice Number: (.*)"
            WHY: (.*) captures everything, too greedy
            GOOD: "pattern": "(?i)Invoice\\\\s*(?:#|Number)?[:\\\\s-]*(INV-?[0-9A-Z-]+|[0-9]{4,})"
            
            BAD: "pattern": "Total: ([0-9]+)"
            WHY: Doesn't handle decimals, commas, or currency
            GOOD: "pattern": "(?i)Total[:\\\\s]*\\\\$?\\\\s*([0-9,]+\\\\.?[0-9]{0,2})"
            
            BAD: "pattern": "Date: ([0-9/]+)"
            WHY: Too loose, could match any numbers with slashes
            GOOD: "pattern": "(?i)Date[:\\\\s-]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})"
            
            BAD: "pattern": "(Customer Name: .+)"
            WHY: Captures the label, not just the value
            GOOD: "pattern": "(?i)Customer\\\\s*Name[:\\\\s-]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\\\\s\\\\.,-]{2,60})"
            
            ═══════════════════════════════════════════════════════════════════════════════
            QUALITY CHECKLIST
            ═══════════════════════════════════════════════════════════════════════════════
            
            Before generating your template, verify:
            
            ✓ Output is PURE JSON (no markdown, no explanations)
            ✓ All patterns use (?i) for case-insensitivity where appropriate
            ✓ Whitespace is flexible (\\\\s* or \\\\s+)
            ✓ Capture groups are around VALUES only, not labels
            ✓ Patterns handle variations (Invoice #, Invoice No., Invoice:, etc.)
            ✓ Number patterns handle commas and decimals
            ✓ Date patterns handle multiple formats
            ✓ All backslashes are properly escaped (doubled in JSON)
            ✓ Critical fields marked as "required": true
            ✓ Transforms include "trim" at minimum
            ✓ Field names use snake_case
            ✓ Blocks are logically organized
            
            ═══════════════════════════════════════════════════════════════════════════════
            FINAL REMINDER
            ═══════════════════════════════════════════════════════════════════════════════
            
            Output ONLY the JSON template. Nothing else. No ```json fences.
            The response should start with { and end with }
            """;

    /**
     * Invoice-specific generation prompt - IMPROVED
     */
    public static final String INVOICE_PROMPT = BASE_SYSTEM_PROMPT + """
            
            ═══════════════════════════════════════════════════════════════════════════════
            INVOICE-SPECIFIC GUIDANCE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Common invoice field labels (consider all variations):
            
            INVOICE NUMBER:
            - Labels: "Invoice #", "Invoice No", "Invoice Number", "Inv #", "Factura", "Factura No", "Comprobante", "Comp. Nro"
            - Pattern: (?i)(?:Invoice|Factura|Comp\\\\.?|Comprobante)\\\\s*(?:#|No\\\\.?|Number|Nro)?[:\\\\s-]*(INV-?[0-9A-Z-]+|[0-9]{4,}|[A-Z]{1,4}-[0-9]{3,})
            
            DATES:
            - Labels: "Date", "Invoice Date", "Issue Date", "Fecha", "Fecha de Emisión"
            - Pattern: (?i)(?:Invoice\\\\s+)?(?:Date|Fecha)(?:\\\\s+de\\\\s+Emisi[oó]n)?[:\\\\s-]*([0-9]{1,2}[-/\\\\.][0-9]{1,2}[-/\\\\.][0-9]{2,4})
            
            DUE DATE:
            - Labels: "Due Date", "Payment Due", "Vencimiento", "Fecha de Vencimiento"
            - Pattern: (?i)(?:Due\\\\s+Date|Payment\\\\s+Due|Vencimiento|Fecha\\\\s+de\\\\s+Vencimiento)[:\\\\s-]*([0-9]{1,2}[-/\\\\.][0-9]{1,2}[-/\\\\.][0-9]{2,4})
            
            TOTAL AMOUNT:
            - Labels: "Total", "Total Amount", "Amount Due", "Importe Total", "Total a Pagar"
            - Pattern: (?i)(?:Total|Importe\\\\s+Total|Amount\\\\s+Due|Total\\\\s+a\\\\s+Pagar)[:\\\\s]*\\\\$?\\\\s*([0-9]{1,3}(?:,?[0-9]{3})*(?:\\\\.[0-9]{2})?)
            
            SUBTOTAL:
            - Labels: "Subtotal", "Sub Total", "Amount", "Importe"
            - Pattern: (?i)(?:Sub\\\\s*Total|Subtotal|Importe)[:\\\\s]*\\\\$?\\\\s*([0-9]{1,3}(?:,?[0-9]{3})*(?:\\\\.[0-9]{2})?)
            
            TAX:
            - Labels: "Tax", "VAT", "IVA", "GST", "Impuestos"
            - Pattern: (?i)(?:Tax|VAT|IVA|GST|Impuestos)[:\\\\s]*\\\\$?\\\\s*([0-9]{1,3}(?:,?[0-9]{3})*(?:\\\\.[0-9]{2})?)
            
            CUSTOMER NAME:
            - Labels: "Bill To", "Customer", "Cliente", "Señor(es)", "Apellido y Nombre", "Razón Social"
            - Pattern: (?i)(?:Bill\\\\s+To|Customer|Cliente|Se[ñn]or(?:es)?|Apellido\\\\s+y\\\\s+Nombre|Raz[oó]n\\\\s+Social)[:\\\\s-]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ0-9\\\\s&,\\\\.'-]{3,80})
            
            CUSTOMER ID:
            - Labels: "Customer ID", "Client #", "ID Cliente", "CUIT", "DNI"
            - Pattern: (?i)(?:Customer\\\\s+ID|Client\\\\s*#|ID\\\\s+Cliente|CUIT|DNI)[:\\\\s-]*([0-9-]{6,})
            
            VENDOR NAME:
            - Usually in header, company name at top
            - Look for largest text or first text in document
            
            PAYMENT TERMS:
            - Labels: "Terms", "Payment Terms", "Condiciones de Pago", "Net 30"
            - Pattern: (?i)(?:Terms|Payment\\\\s+Terms|Condiciones(?:\\\\s+de\\\\s+Pago)?)[:\\\\s-]*(Net\\\\s+[0-9]+|[0-9]+\\\\s+days?|[0-9]+\\\\s+d[ií]as?)
            
            Create blocks:
            1. "header" - invoice_number, issue_date
            2. "parties" - customer_name, customer_id, vendor_name
            3. "amounts" - subtotal, tax, total, amount_due
            4. "terms" - payment_terms, due_date
            """;

    /**
     * Receipt-specific generation prompt - IMPROVED
     */
    public static final String RECEIPT_PROMPT = BASE_SYSTEM_PROMPT + """
            
            ═══════════════════════════════════════════════════════════════════════════════
            RECEIPT-SPECIFIC GUIDANCE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Common receipt field labels:
            
            RECEIPT NUMBER:
            - Labels: "Receipt #", "Transaction #", "Recibo", "Ticket"
            - Pattern: (?i)(?:Receipt|Transaction|Recibo|Ticket)\\\\s*#?[:\\\\s-]*(R-?[0-9A-Z]+|[0-9]{4,})
            
            DATE:
            - Labels: "Date", "Transaction Date", "Fecha"
            - Pattern: (?i)Date[:\\\\s-]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})
            
            TIME:
            - Pattern: ([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\\\s*[AP]M)?)
            
            STORE/MERCHANT NAME:
            - Usually at top of receipt
            - Pattern: First significant text block
            
            TOTAL:
            - Labels: "Total", "Amount Paid", "Total Paid"
            - Pattern: (?i)Total(?:\\\\s+Paid)?[:\\\\s]*\\\\$?\\\\s*([0-9,]+\\\\.?[0-9]{0,2})
            
            PAYMENT METHOD:
            - Labels: "Payment Method", "Paid by", "Método de Pago"
            - Pattern: (?i)(?:Payment\\\\s+Method|Paid\\\\s+by|M[ée]todo\\\\s+de\\\\s+Pago)[:\\\\s-]*(Cash|Card|Credit|D[ée]bito|Cr[ée]dito|Efectivo)
            
            CASHIER:
            - Labels: "Cashier", "Served by", "Cajero"
            - Pattern: (?i)(?:Cashier|Served\\\\s+by|Cajero)[:\\\\s-]*([A-Z][a-z]+(?:\\\\s+[A-Z][a-z]+)?)
            
            Create blocks:
            1. "header" - receipt_number, date, time
            2. "merchant" - store_name, store_location
            3. "transaction" - total, payment_method
            4. "additional" - cashier
            """;

    /**
     * Purchase order-specific generation prompt - IMPROVED
     */
    public static final String PURCHASE_ORDER_PROMPT = BASE_SYSTEM_PROMPT + """
            
            ═══════════════════════════════════════════════════════════════════════════════
            PURCHASE ORDER-SPECIFIC GUIDANCE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Common PO field labels:
            
            PO NUMBER:
            - Labels: "PO #", "Purchase Order", "Order #", "Orden de Compra"
            - Pattern: (?i)(?:PO|Purchase\\\\s+Order|Order|Orden\\\\s+de\\\\s+Compra)\\\\s*#?[:\\\\s-]*(PO-?[0-9A-Z]+|ORDER-?[0-9]+|[0-9]{4,})
            
            ORDER DATE:
            - Labels: "Date", "Order Date", "Fecha de Orden"
            - Pattern: (?i)(?:Order\\\\s+)?Date[:\\\\s-]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})
            
            VENDOR:
            - Labels: "Vendor", "Supplier", "Proveedor", "Sold By"
            - Pattern: (?i)(?:Vendor|Supplier|Proveedor|Sold\\\\s+By)[:\\\\s-]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\\\\s&,\\\\.'-]{3,60})
            
            BUYER:
            - Labels: "Buyer", "Purchaser", "Comprador", "Requested By"
            - Pattern: (?i)(?:Buyer|Purchaser|Comprador|Requested\\\\s+By)[:\\\\s-]*([A-Z][a-z]+(?:\\\\s+[A-Z][a-z]+)?)
            
            DELIVERY DATE:
            - Labels: "Delivery Date", "Expected Delivery", "Fecha de Entrega"
            - Pattern: (?i)(?:Delivery\\\\s+Date|Expected\\\\s+Delivery|Fecha\\\\s+de\\\\s+Entrega)[:\\\\s-]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})
            
            TOTAL:
            - Labels: "Total", "Order Total", "Total Amount"
            - Pattern: (?i)(?:Order\\\\s+)?Total[:\\\\s]*\\\\$?\\\\s*([0-9,]+\\\\.?[0-9]{0,2})
            
            Create blocks:
            1. "header" - po_number, order_date
            2. "parties" - vendor_name, buyer_name
            3. "delivery" - delivery_date, shipping_address
            4. "amounts" - subtotal, tax, total
            """;

    /**
     * Generic/unknown document type prompt - IMPROVED
     */
    public static final String GENERIC_PROMPT = BASE_SYSTEM_PROMPT + """
            
            ═══════════════════════════════════════════════════════════════════════════════
            GENERIC DOCUMENT GUIDANCE
            ═══════════════════════════════════════════════════════════════════════════════
            
            Since the document type is unknown or generic, follow these strategies:
            
            1. IDENTIFY KEY IDENTIFIERS
               - Look for any reference numbers, IDs, codes
               - Pattern: (?i)(?:Number|No|#|ID|Ref|Reference)[:\\\\s-]*([A-Z0-9-]{3,})
            
            2. FIND DATES
               - Multiple formats: DD/MM/YYYY, MM-DD-YYYY, YYYY.MM.DD
               - Pattern: ([0-9]{1,2}[-/\\\\.][0-9]{1,2}[-/\\\\.][0-9]{2,4}|[0-9]{4}[-/\\\\.][0-9]{1,2}[-/\\\\.][0-9]{1,2})
            
            3. EXTRACT AMOUNTS
               - Any monetary values
               - Pattern: \\\\$?\\\\s*([0-9]{1,3}(?:,?[0-9]{3})*(?:\\\\.[0-9]{2})?)
            
            4. CAPTURE NAMES/ENTITIES
               - Company names, person names
               - Pattern: ([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ\\\\s&,\\\\.'-]{3,60})
            
            5. LOGICAL GROUPING
               - Group related fields into blocks
               - "identifiers", "dates", "amounts", "parties", "metadata"
            
            Be conservative: Only extract fields you can reliably identify with high confidence.
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
        } else if (desc.contains("receipt") || desc.contains("recibo") || desc.contains("ticket")) {
            return RECEIPT_PROMPT;
        } else if (desc.contains("purchase order") || desc.contains("po ") ||
                desc.contains("orden de compra")) {
            return PURCHASE_ORDER_PROMPT;
        } else {
            return GENERIC_PROMPT;
        }
    }
}
