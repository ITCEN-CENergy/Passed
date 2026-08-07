CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token VARCHAR(512) NOT NULL,

    CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (refresh_token),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
