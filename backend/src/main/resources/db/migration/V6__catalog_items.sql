CREATE TABLE catalog_items (
       id UUID PRIMARY KEY,
       name VARCHAR(120) NOT NULL,
       code VARCHAR(50) NOT NULL UNIQUE,
       detail VARCHAR(500),
       type VARCHAR(30) NOT NULL,              -- PREFERENCE o CATEGORY
       status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
       CONSTRAINT chk_catalog_type CHECK (type IN ('PREFERENCE', 'CATEGORY')),
       CONSTRAINT chk_catalog_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Regla 4: nombre único POR TIPO (PostgreSQL: índice único)
CREATE UNIQUE INDEX uk_catalog_name_type ON catalog_items (LOWER(name), type);

CREATE INDEX idx_catalog_type ON catalog_items(type);
