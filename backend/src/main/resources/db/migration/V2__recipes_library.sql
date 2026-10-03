-- Stage 2: recipes, ingredients, steps, tags, folders, images.
-- Schema: docs/database-schema.md

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Uploaded files. The file itself lives on disk (local) or in S3 (prod); here only metadata.
CREATE TABLE images (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    storage_key  VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   INTEGER      NOT NULL CHECK (size_bytes > 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_images_user ON images (user_id);

CREATE TABLE recipes (
    id                BIGSERIAL PRIMARY KEY,
    -- NULL = recipe from the app catalog (stage 3)
    user_id           BIGINT       REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    description       TEXT,
    servings          INTEGER      CHECK (servings BETWEEN 1 AND 100),
    total_time_min    INTEGER      CHECK (total_time_min BETWEEN 0 AND 2880),
    source_url        VARCHAR(2000),
    source_author     VARCHAR(200),
    source_type       VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'
                      CHECK (source_type IN ('MANUAL', 'WEB', 'YOUTUBE', 'IMAGE', 'CATALOG')),
    catalog_recipe_id BIGINT       REFERENCES recipes (id) ON DELETE SET NULL,
    language          VARCHAR(10),
    cover_image_id    BIGINT       REFERENCES images (id) ON DELETE SET NULL,
    notes             TEXT,
    is_favorite       BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);

-- Library page: my live recipes, newest first (keyset pagination on updated_at, id)
CREATE INDEX ix_recipes_user_updated ON recipes (user_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
-- Search by part of a word and with typos
CREATE INDEX ix_recipes_title_trgm ON recipes USING gin (lower(title) gin_trgm_ops);

CREATE TABLE recipe_ingredients (
    id               BIGSERIAL PRIMARY KEY,
    recipe_id        BIGINT        NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    position         INTEGER       NOT NULL CHECK (position >= 0),
    raw_text         VARCHAR(500)  NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    quantity         NUMERIC(10, 3) CHECK (quantity > 0),
    unit             VARCHAR(30),
    note             VARCHAR(300),
    category         VARCHAR(50),
    -- v2: ingredient is another recipe (pizza -> dough)
    linked_recipe_id BIGINT        REFERENCES recipes (id) ON DELETE SET NULL,
    UNIQUE (recipe_id, position)
);

CREATE INDEX ix_ingredients_name_trgm ON recipe_ingredients USING gin (lower(name) gin_trgm_ops);

CREATE TABLE recipe_steps (
    id           BIGSERIAL PRIMARY KEY,
    recipe_id    BIGINT  NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL CHECK (position >= 0),
    text         TEXT    NOT NULL,
    ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (recipe_id, position)
);

CREATE TABLE tags (
    id      BIGSERIAL PRIMARY KEY,
    -- NULL = catalog tag
    user_id BIGINT      REFERENCES users (id) ON DELETE CASCADE,
    name    VARCHAR(50) NOT NULL CHECK (length(trim(name)) > 0)
);

-- One tag name per user, case-insensitive. NULLS NOT DISTINCT: also unique among catalog tags.
CREATE UNIQUE INDEX ux_tags_user_name ON tags (user_id, lower(name)) NULLS NOT DISTINCT;
CREATE INDEX ix_tags_name_trgm ON tags USING gin (lower(name) gin_trgm_ops);

CREATE TABLE recipe_tags (
    recipe_id BIGINT NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    tag_id    BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (recipe_id, tag_id)
);

CREATE INDEX ix_recipe_tags_tag ON recipe_tags (tag_id);

CREATE TABLE folders (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_folders_user_name ON folders (user_id, lower(name));

CREATE TABLE folder_recipes (
    folder_id BIGINT      NOT NULL REFERENCES folders (id) ON DELETE CASCADE,
    recipe_id BIGINT      NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (folder_id, recipe_id)
);

CREATE INDEX ix_folder_recipes_recipe ON folder_recipes (recipe_id);
