-- =======================================================================================
-- ==                                   AI Social Hub                                 ==
-- ==                          Version: 2025-11-23                                    ==
-- =======================================================================================
-- Feature Include：
-- 1. 双主键：内部使用 BIGSERIAL，外部使用 UUID。
-- 2. 软删除：使用 'deleted_at' 字段以支持可恢复的数据，并配合性能优化的部分索引。
-- 3. 枚举类型：使用 ENUM 确保数据一致性。
-- 4. 面向未来的灵活性：'vip_level' 保持为 VARCHAR 以便扩展。
-- =======================================================================================

-- 0. 启用所需扩展
CREATE
    EXTENSION IF NOT EXISTS vector;
CREATE
    EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. 创建所有 ENUM 类型
CREATE TYPE conversation_type_enum AS ENUM ('PRIVATE', 'GROUP');
CREATE TYPE media_type_enum AS ENUM ('TEXT', 'IMAGE', 'VOICE', 'VIDEO', 'FILE');
CREATE TYPE ai_task_status_enum AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE ai_task_type_enum AS ENUM ('POLISH', 'SCHEDULE_EXTRACTION', 'PERSONA_ANALYSIS', 'SPEECH_TO_TEXT', 'CHAT_SUMMARY', 'CONTENT_MODERATION', 'SMART_REPLY', 'TRANSLATION', 'SUMMARIZATION');
CREATE TYPE friendship_request_status_enum AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED');
CREATE TYPE conversation_member_role_enum AS ENUM ('MEMBER', 'ADMIN', 'OWNER');

-- =======================================================================================
-- ==                              CORE BUSINESS TABLES                                 ==
-- =======================================================================================

-- 2. 用户表 (users)
CREATE TABLE users
(
    id                  BIGSERIAL PRIMARY KEY,
    public_id           VARCHAR(50) UNIQUE NOT NULL,
    username            VARCHAR(64) UNIQUE NOT NULL,
    nickname            VARCHAR(64),
    avatar_url          TEXT,
    password_hash       TEXT               NOT NULL,
    email               VARCHAR(255) UNIQUE,
    phone               VARCHAR(20) UNIQUE,
    vip_level           VARCHAR(20)                 DEFAULT 'FREE',
    ai_analysis_enabled BOOLEAN                     DEFAULT false,
    created_at          TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ                 DEFAULT NULL
);
COMMENT
    ON TABLE users IS '用户主表';
CREATE INDEX idx_users_public_id ON users (public_id);
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_active ON users (id) WHERE deleted_at IS NULL;


-- 3. 会话表 (conversations)
CREATE TABLE conversations
(
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(50) UNIQUE     NOT NULL,
    type              conversation_type_enum NOT NULL,
    name              VARCHAR(100),
    member_count      INTEGER                         DEFAULT 0,
    latest_message_id BIGINT                          DEFAULT 0,
    created_at        TIMESTAMPTZ            NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ                     DEFAULT NULL
);
COMMENT
    ON TABLE conversations IS '会话表（私聊/群聊）';
CREATE INDEX idx_conv_public_id ON conversations (public_id);
CREATE INDEX idx_conv_type ON conversations (type);
CREATE INDEX idx_conversations_active ON conversations (id) WHERE deleted_at IS NULL;


-- 4. 会话成员关系表 (conversation_members)
CREATE TABLE conversation_members
(
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT                        NOT NULL REFERENCES conversations (id),
    user_id              BIGINT                        NOT NULL REFERENCES users (id),
    joined_at            TIMESTAMPTZ                   NOT NULL DEFAULT NOW(),
    -- 角色：OWNER（创建者），ADMIN（管理员），MEMBER（普通成员）
    role                 conversation_member_role_enum NOT NULL DEFAULT 'MEMBER',
    last_read_message_id BIGINT                                 DEFAULT 0,
    last_read_at         TIMESTAMPTZ                            DEFAULT NOW()
);
COMMENT
    ON TABLE conversation_members IS '会话成员关系表';
CREATE INDEX idx_members_user ON conversation_members (user_id);
CREATE UNIQUE INDEX idx_members_unique ON conversation_members (conversation_id, user_id);
CREATE INDEX idx_conv_members_lastread ON conversation_members (conversation_id, user_id, last_read_message_id);


-- 5. 好友请求表 (friendship_requests)
CREATE TABLE friendship_requests
(
    id          BIGSERIAL PRIMARY KEY,
    public_id   VARCHAR(50) UNIQUE             NOT NULL,
    sender_id   BIGINT                         NOT NULL REFERENCES users (id),
    receiver_id BIGINT                         NOT NULL REFERENCES users (id),
    message     TEXT,
    status      friendship_request_status_enum NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ                    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ                    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sender_receiver UNIQUE (sender_id, receiver_id) -- 防止重复发送请求
);
COMMENT
    ON TABLE friendship_requests IS '好友请求表，管理请求的生命周期';
CREATE INDEX idx_friendship_requests_receiver_status ON friendship_requests (receiver_id, status);


-- 6. 好友关系表 (friendships)
-- 用于快速查询好友关系，是 friendship_requests 中 status='ACCEPTED' 的结果集
CREATE TABLE friendships
(
    user_id_1  BIGINT      NOT NULL REFERENCES users (id),
    user_id_2  BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id_1, user_id_2),
    CONSTRAINT check_users_order CHECK (user_id_1 < user_id_2) -- 确保(1,2)和(2,1)不会同时存在
);
COMMENT
    ON TABLE friendships IS '好友关系表，用于快速查询好友列表';
CREATE INDEX idx_friendships_user2 ON friendships (user_id_2);


-- 7. 消息表 (messages)
CREATE TABLE messages
(
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id),
    sender_id       BIGINT      NOT NULL REFERENCES users (id),
    content         TEXT,
    media_type      media_type_enum      DEFAULT 'TEXT',
    media_url       TEXT,
    source_type     VARCHAR(30), -- 例如 'AI_POLISHED', 'SPEECH_TRANSCRIPT'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ          DEFAULT NULL
);
COMMENT
    ON TABLE messages IS '消息表';
COMMENT
    ON COLUMN messages.source_type IS '表明此消息的来源，用于UI区分展示';
CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_active ON messages (id) WHERE deleted_at IS NULL;


-- 8. 日程事件表 (schedules)
CREATE TABLE schedules
(
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(50) UNIQUE NOT NULL,
    user_id           BIGINT             NOT NULL REFERENCES users (id),
    title             VARCHAR(200)       NOT NULL,
    description       TEXT,
    start_time        TIMESTAMPTZ        NOT NULL,
    end_time          TIMESTAMPTZ        NOT NULL,
    location          VARCHAR(200),
    is_ai_extracted   BOOLEAN                     DEFAULT false,
    source_message_id BIGINT             REFERENCES messages (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);
COMMENT
    ON TABLE schedules IS '日程事件表';
CREATE INDEX idx_schedules_user_time ON schedules (user_id, start_time);


-- 9. 异步AI任务追踪表 (ai_tasks)
CREATE TABLE ai_tasks
(
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(50) UNIQUE  NOT NULL,
    user_id           BIGINT              NOT NULL REFERENCES users (id),
    task_type         ai_task_type_enum   NOT NULL,
    task_status       ai_task_status_enum NOT NULL DEFAULT 'PENDING',
    source_message_id BIGINT REFERENCES messages (id),
    input_payload     JSONB,
    output_payload    JSONB,
    error_message     TEXT,
    -- AI配置相关字段
    model_config      JSONB,          -- 执行任务时使用的模型配置
    provider          VARCHAR(50),    -- AI服务提供商
    token_usage       JSONB,          -- 令牌使用统计
    cost              DECIMAL(10, 4), -- 任务执行成本
    created_at        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
COMMENT
    ON TABLE ai_tasks IS '追踪所有异步AI任务的状态，用于前端展示与后端管理';
COMMENT
    ON COLUMN ai_tasks.model_config IS '执行任务时使用的模型配置';
COMMENT
    ON COLUMN ai_tasks.provider IS 'AI服务提供商';
COMMENT
    ON COLUMN ai_tasks.token_usage IS '令牌使用统计';
COMMENT
    ON COLUMN ai_tasks.cost IS '任务执行成本';
CREATE INDEX idx_ai_tasks_user_status ON ai_tasks (user_id, task_status);
CREATE INDEX idx_ai_tasks_type_status ON ai_tasks (task_type, task_status);


-- 10. 用户画像配置表 (user_ai_profiles)
CREATE TABLE user_ai_profiles
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    profile_type  VARCHAR(50) NOT NULL, -- PERSONA, PREFERENCES, BEHAVIOR, etc.
    model_name    VARCHAR(100),         -- 生成此配置的模型名称
    model_version VARCHAR(100),         -- 生成此配置的具体模型版本
    provider      VARCHAR(50),          -- 生成此配置的AI服务提供商
    content       JSONB       NOT NULL, -- 结构化配置内容
    embedding     VECTOR(1536),         -- 向量表示，用于相似度搜索
    version       INT         NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT
    ON TABLE user_ai_profiles IS '用户AI配置表，统一存储用户AI相关配置、画像和向量';
COMMENT
    ON COLUMN user_ai_profiles.profile_type IS '配置类型：PERSONA（性格画像）, PREFERENCES（偏好）, BEHAVIOR（行为分析）等';
COMMENT
    ON COLUMN user_ai_profiles.model_name IS '生成此配置的模型名称和版本，用于平滑升级';
COMMENT
    ON COLUMN user_ai_profiles.model_version IS '生成此配置的具体模型版本';
COMMENT
    ON COLUMN user_ai_profiles.provider IS '生成此配置的AI服务提供商';
COMMENT
    ON COLUMN user_ai_profiles.embedding IS '向量表示，用于相似度搜索、推荐等RAG功能';
CREATE UNIQUE INDEX idx_ai_profile_user_type ON user_ai_profiles (user_id, profile_type);
CREATE INDEX idx_ai_profile_embedding ON user_ai_profiles USING hnsw (embedding vector_l2_ops) WHERE embedding IS NOT NULL;


-- 11. AI用户配置表（ai_user_configs）
CREATE TABLE ai_user_configs
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT UNIQUE NOT NULL REFERENCES users (id),
    default_model      VARCHAR(100) DEFAULT 'gpt-4o',
    default_provider   VARCHAR(50)  DEFAULT 'openai',
    config_params      JSONB        DEFAULT '{
      "temperature": 0.7,
      "max_tokens": 1000
    }',
    api_keys_encrypted JSONB, -- 加密存储不同提供商的API key
    is_active          BOOLEAN      DEFAULT TRUE,
    created_at         TIMESTAMPTZ  DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  DEFAULT NOW()
);
COMMENT ON TABLE ai_user_configs IS 'AI用户配置表，存储用户的AI模型和API配置';
CREATE INDEX idx_ai_user_configs_user ON ai_user_configs (user_id);

-- 12. AI提供商配置表（ai_provider_configs）
CREATE TABLE ai_provider_configs
(
    id             BIGSERIAL PRIMARY KEY,
    provider_name  VARCHAR(50) UNIQUE NOT NULL, -- openai, anthropic, azure, dashscope
    display_name   VARCHAR(100),
    api_base_url   VARCHAR(255),
    is_enabled     BOOLEAN     DEFAULT TRUE,
    default_config JSONB,                       -- 默认配置参数（如 temperature, top_p 等）
    created_at     TIMESTAMPTZ DEFAULT NOW(),
    updated_at     TIMESTAMPTZ DEFAULT NOW()
);
COMMENT ON TABLE ai_provider_configs IS 'AI提供商系统配置表';


-- 13. AI使用统计表（ai_usage_stats）
CREATE TABLE ai_usage_stats
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    task_id       BIGINT REFERENCES ai_tasks (id),
    provider      VARCHAR(50)  NOT NULL,
    model_name    VARCHAR(100) NOT NULL,
    tokens_used   INTEGER      NOT NULL,
    input_tokens  INTEGER,
    output_tokens INTEGER,
    cost          DECIMAL(10, 4),
    date          DATE         NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT NOW()
);
COMMENT ON TABLE ai_usage_stats IS 'AI使用统计表，用于计费和用量分析';
CREATE INDEX idx_ai_usage_user_date ON ai_usage_stats (user_id, date);
CREATE INDEX idx_ai_usage_provider_date ON ai_usage_stats (provider, date);


-- 14. AI模型能力表（ai_model_capabilities）
CREATE TABLE ai_model_capabilities
(
    id                       BIGSERIAL PRIMARY KEY,
    model_name               VARCHAR(100)      NOT NULL,
    provider                 VARCHAR(50)       NOT NULL REFERENCES ai_provider_configs (provider_name),
    capability_type          ai_task_type_enum NOT NULL,
    is_enabled               BOOLEAN        DEFAULT TRUE,
    max_tokens               INTEGER,
    input_price_per_million  DECIMAL(10, 4),
    output_price_per_million DECIMAL(10, 4),
    created_at               TIMESTAMPTZ    DEFAULT NOW()
);
COMMENT ON TABLE ai_model_capabilities IS 'AI模型能力定义表，细化到每个模型在不同任务下的定价和限制';
CREATE UNIQUE INDEX idx_model_capability ON ai_model_capabilities (model_name, capability_type);
COMMENT ON TABLE ai_model_capabilities IS 'AI模型能力定义表';
CREATE UNIQUE INDEX idx_model_capability ON ai_model_capabilities (model_name, capability_type);

-- =======================================================================================
-- ==                                 END OF SCHEMA                                     ==
-- =======================================================================================

-- 插入默认提供商配置
INSERT INTO ai_provider_configs (provider_name, display_name, api_base_url, is_enabled, default_config)
VALUES ('openai', 'OpenAI', 'https://api.openai.com/v1', true, '{
  "temperature": 0.7,
  "max_tokens": 1000
}'),
       ('anthropic', 'Anthropic', 'https://api.anthropic.com/v1', true, '{
         "temperature": 0.7,
         "max_tokens": 1000
       }');