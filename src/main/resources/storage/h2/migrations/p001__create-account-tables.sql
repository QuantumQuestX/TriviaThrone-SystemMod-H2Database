CREATE TABLE IF NOT EXISTS user_accounts (
    user_id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_account_roles (
    username VARCHAR(255) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (username, role_name),
    CONSTRAINT fk_user_account_roles_user
        FOREIGN KEY (username) REFERENCES user_accounts(username) ON DELETE CASCADE
);
