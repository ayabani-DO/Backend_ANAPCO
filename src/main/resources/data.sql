-- Insert default roles
INSERT INTO role (name, create_date, last_modified_date) VALUES 
('ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('OPS_MANAGER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FINANCE_CONTROLLER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('VIEWER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default admin user (password: admin123)
INSERT INTO user (first_name, last_name, email, password, enabled, account_locked, create_date, last_modified_date) VALUES 
('Admin', 'User', 'admin@anapco.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Assign admin role to admin user
INSERT INTO user_roles (user_id, role_id) VALUES 
(1, 1);
