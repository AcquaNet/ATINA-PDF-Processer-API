-- ============================================
-- Migration: Add subject fields for email notifications
-- Version: 1.0.4
-- Description: Add configurable subject lines for received and processed email notifications
-- ============================================

-- Add subject field for "received" notification
ALTER TABLE email_sender_rules
ADD COLUMN subject_email_received VARCHAR(200) AFTER template_email_received;

-- Add subject field for "processed" notification
ALTER TABLE email_sender_rules
ADD COLUMN subject_email_processed VARCHAR(200) AFTER template_email_processed;

-- Set default subjects for existing records
UPDATE email_sender_rules
SET subject_email_received = 'Receipt Confirmation - Documents Received'
WHERE subject_email_received IS NULL;

UPDATE email_sender_rules
SET subject_email_processed = 'Processing Complete - Extraction Results'
WHERE subject_email_processed IS NULL;

-- Add comments for documentation
COMMENT ON COLUMN email_sender_rules.subject_email_received IS 'Subject line for email sent when documents are received';
COMMENT ON COLUMN email_sender_rules.subject_email_processed IS 'Subject line for email sent when processing is complete';
