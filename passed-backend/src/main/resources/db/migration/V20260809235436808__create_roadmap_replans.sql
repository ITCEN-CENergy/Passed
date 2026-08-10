CREATE TABLE roadmap_replans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token UUID NOT NULL,
    roadmap_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary TEXT NOT NULL,
    decisions_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMPTZ,

    CONSTRAINT fk_roadmap_replans_roadmap
        FOREIGN KEY (roadmap_id) REFERENCES roadmaps(id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_replans_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_roadmap_replans_token UNIQUE (token),
    CONSTRAINT ck_roadmap_replans_status
        CHECK (status IN ('READY', 'APPLIED'))
);

CREATE INDEX idx_roadmap_replans_roadmap_created
    ON roadmap_replans(roadmap_id, created_at DESC);
