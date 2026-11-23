CREATE TABLE item_permissions (
    id BINARY(16) NOT NULL,
    item_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    role ENUM('VIEWER', 'EDITOR') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_permissions_item_user UNIQUE (item_id, user_id),
    INDEX ix_item_permissions_user_item (user_id, item_id),
    CONSTRAINT fk_item_permissions_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_item_permissions_user FOREIGN KEY (user_id) REFERENCES users (id)
);
