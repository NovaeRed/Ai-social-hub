-- Review before applying to the connected database. No existing rows are changed.
-- Prerequisites: users table and the vector extension (already confirmed installed).
BEGIN;
SELECT pg_advisory_xact_lock(20261023, 1);
CREATE TABLE IF NOT EXISTS user_ai_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    profile_type VARCHAR(50) NOT NULL,
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    provider VARCHAR(50),
    content JSONB NOT NULL,
    confidence DECIMAL(5,4),
    source_message_count INTEGER NOT NULL DEFAULT 0,
    source_time_from TIMESTAMPTZ,
    source_time_to TIMESTAMPTZ,
    last_analyzed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    embedding VECTOR(1536),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_profile_user_type ON user_ai_profiles(user_id, profile_type);
CREATE INDEX IF NOT EXISTS idx_ai_profile_analyzed_at ON user_ai_profiles(user_id, last_analyzed_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_profile_embedding ON user_ai_profiles USING hnsw(embedding vector_l2_ops) WHERE embedding IS NOT NULL;
COMMIT;
