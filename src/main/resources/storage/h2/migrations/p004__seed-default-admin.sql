INSERT INTO user_accounts (user_id, username, display_name, password_hash, enabled, default_admin)
SELECT '00000000000040008000000000000001',
       'admin',
       'Default Administrator',
       '$2a$10$LIYDIuxP3Stc2pPxjufvfu01YUKZO12zm/qu9jqOktZQ/Meg9u2Ya',
       TRUE,
       TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM user_accounts WHERE username = 'admin'
);

UPDATE user_accounts
SET display_name = 'Default Administrator',
    password_hash = '$2a$10$LIYDIuxP3Stc2pPxjufvfu01YUKZO12zm/qu9jqOktZQ/Meg9u2Ya',
    default_admin = TRUE
WHERE username = 'admin';

INSERT INTO user_account_roles (username, role_name)
SELECT 'admin', 'USER'
WHERE NOT EXISTS (
    SELECT 1 FROM user_account_roles WHERE username = 'admin' AND role_name = 'USER'
);

INSERT INTO user_account_roles (username, role_name)
SELECT 'admin', 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM user_account_roles WHERE username = 'admin' AND role_name = 'ADMIN'
);
