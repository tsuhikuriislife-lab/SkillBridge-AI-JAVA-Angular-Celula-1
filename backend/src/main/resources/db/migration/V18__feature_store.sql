CREATE TABLE feature_batches (
    batch_id UUID PRIMARY KEY,
    estado VARCHAR(20) NOT NULL,
    mentores_cargados INTEGER NOT NULL DEFAULT 0,
    embeddings_cargados INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    CONSTRAINT chk_feature_batches_estado
        CHECK (estado IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_feature_batches_mentores_cargados
        CHECK (mentores_cargados >= 0),
    CONSTRAINT chk_feature_batches_embeddings_cargados
        CHECK (embeddings_cargados >= 0)
);

CREATE TABLE mentor_features (
    mentor_id UUID PRIMARY KEY,
    compatibility_score REAL NOT NULL DEFAULT 0,
    expertise_score REAL NOT NULL DEFAULT 0,
    availability_score REAL NOT NULL DEFAULT 0,
    popularity_score REAL NOT NULL DEFAULT 0,
    batch_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_mentor_features_mentor
        FOREIGN KEY (mentor_id) REFERENCES app_users(id),
    CONSTRAINT fk_mentor_features_batch
        FOREIGN KEY (batch_id) REFERENCES feature_batches(batch_id),
    CONSTRAINT chk_mentor_features_compatibility_score
        CHECK (compatibility_score BETWEEN 0 AND 1),
    CONSTRAINT chk_mentor_features_expertise_score
        CHECK (expertise_score BETWEEN 0 AND 1),
    CONSTRAINT chk_mentor_features_availability_score
        CHECK (availability_score BETWEEN 0 AND 1),
    CONSTRAINT chk_mentor_features_popularity_score
        CHECK (popularity_score BETWEEN 0 AND 1)
);

CREATE INDEX idx_mentor_features_batch_id ON mentor_features(batch_id);

CREATE TABLE service_embeddings (
    service_id UUID PRIMARY KEY,
    embedding BYTEA NOT NULL,
    dimension INTEGER NOT NULL,
    batch_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_service_embeddings_service
        FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT fk_service_embeddings_batch
        FOREIGN KEY (batch_id) REFERENCES feature_batches(batch_id),
    CONSTRAINT chk_service_embeddings_dimension
        CHECK (dimension > 0),
    CONSTRAINT chk_service_embeddings_float32_length
        CHECK (octet_length(embedding) = dimension * 4)
);

CREATE INDEX idx_service_embeddings_batch_id ON service_embeddings(batch_id);
