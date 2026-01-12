-- ============================================
-- INVOICE EXTRACTOR API - INITIAL DATA
-- ============================================
-- This file is automatically executed on startup

-- Insert default users
-- Password: admin123 (BCrypt encoded)
INSERT INTO users (username, password, email, full_name, enabled, created_at) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@example.com', 'Administrator', true, CURRENT_TIMESTAMP),
('user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'user@example.com', 'Regular User', true, CURRENT_TIMESTAMP);

-- Note: In production, you should:
-- 1. Change these passwords
-- 2. Use environment variables for sensitive data
-- 3. Implement proper user management

