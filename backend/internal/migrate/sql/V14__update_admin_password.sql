-- Update default admin user password from "admin" to "admin1"
-- New password hash is bcrypt hash of "admin1" with strength 10
UPDATE users
SET password_hash = '$2a$10$j6u7LLRjQfVQIlu4FJbQtulaQTGxqoSP.dEVQmEIr2rjocFyM3zOe'
WHERE email = 'admin@timetrack.local';
