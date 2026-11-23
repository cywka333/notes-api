CREATE TABLE items (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    version INT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_items_owner_deleted (owner_id, deleted),
    CONSTRAINT fk_items_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);
