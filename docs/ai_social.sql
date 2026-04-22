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
    announcement      TEXT,
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
    last_read_at         TIMESTAMPTZ                            DEFAULT NOW(),
    cleared_message_id   BIGINT                                 DEFAULT 0
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
-- 7.1 聊天文件表 (chat_files)
CREATE TABLE chat_files
(
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(50) UNIQUE NOT NULL,
    uploader_id       BIGINT             NOT NULL REFERENCES users (id),
    conversation_id   BIGINT REFERENCES conversations (id) ON DELETE SET NULL,
    access_url        TEXT               NOT NULL,
    original_filename VARCHAR(255)       NOT NULL,
    file_ext          VARCHAR(20)        NOT NULL,
    content_type      VARCHAR(127)       NOT NULL,
    size_bytes        BIGINT             NOT NULL CHECK (size_bytes >= 0),
    created_at        TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ                 DEFAULT NULL
);
COMMENT
    ON TABLE chat_files IS '聊天文件表，用于消息中的文件上传与引用';
COMMENT
    ON COLUMN chat_files.file_ext IS '文件扩展名（不含.，如pdf/docx/png）';
CREATE INDEX idx_chat_files_public_id ON chat_files (public_id);
CREATE INDEX idx_chat_files_conversation_created ON chat_files (conversation_id, created_at DESC);
CREATE INDEX idx_chat_files_uploader_created ON chat_files (uploader_id, created_at DESC);
CREATE INDEX idx_chat_files_active ON chat_files (id) WHERE deleted_at IS NULL;

-- 7.2 消息表 (messages)
CREATE TABLE messages
(
    id              BIGSERIAL PRIMARY KEY,
    public_id       VARCHAR(50) UNIQUE NOT NULL,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id),
    sender_id       BIGINT      NOT NULL REFERENCES users (id),
    content         TEXT,
    media_type      media_type_enum      DEFAULT 'TEXT',
    media_url       TEXT,
    file_id         BIGINT REFERENCES chat_files (id) ON DELETE SET NULL,
    source_type     VARCHAR(30), -- 例如 'AI_POLISHED', 'SPEECH_TRANSCRIPT'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ          DEFAULT NULL
);
COMMENT
    ON TABLE messages IS '消息表';
COMMENT
    ON COLUMN messages.source_type IS '表明此消息的来源，用于UI区分展示';
COMMENT
    ON COLUMN messages.file_id IS '文件消息关联的文件资产ID（media_type=FILE时应有值）';
CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_active ON messages (id) WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_file_id ON messages (file_id);


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
CREATE INDEX idx_schedules_user_time ON schedules (user_id, start_time, end_time);


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
    model_config      JSONB,          -- 执行任务时使用的模型配置（至少包含 model_name）
    provider          VARCHAR(50),    -- 解析后的AI服务提供商（resolved_provider）
    token_usage       JSONB,          -- 令牌使用统计
    cost              DECIMAL(10, 4), -- 任务执行成本
    created_at        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
COMMENT
    ON TABLE ai_tasks IS '追踪所有异步AI任务的状态，用于前端展示与后端管理';
COMMENT
    ON COLUMN ai_tasks.model_config IS '执行任务时使用的模型配置，建议至少包含 model_name 以支持审计';
COMMENT
    ON COLUMN ai_tasks.provider IS '任务执行时解析得到的模型提供商（resolved_provider）';
COMMENT
    ON COLUMN ai_tasks.token_usage IS '令牌使用统计';
COMMENT
    ON COLUMN ai_tasks.cost IS '任务执行成本';
CREATE INDEX idx_ai_tasks_user_status ON ai_tasks (user_id, task_status);
CREATE INDEX idx_ai_tasks_type_status ON ai_tasks (task_type, task_status);


-- 10. 用户画像配置表 (user_ai_profiles)
CREATE TABLE user_ai_profiles
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users (id),
    profile_type         VARCHAR(50) NOT NULL,           -- PERSONA, PREFERENCES, BEHAVIOR, etc.
    model_name           VARCHAR(100),                   -- 生成此配置的模型名称
    model_version        VARCHAR(100),                   -- 生成此配置的具体模型版本
    provider             VARCHAR(50),                    -- 生成此配置的AI服务提供商
    content              JSONB       NOT NULL,           -- 可扩展画像内容
    confidence           DECIMAL(5, 4),                  -- 画像可信度（0~1）
    source_message_count INTEGER     NOT NULL DEFAULT 0, -- 本次分析使用的消息数
    source_time_from     TIMESTAMPTZ,                    -- 样本时间窗口起点
    source_time_to       TIMESTAMPTZ,                    -- 样本时间窗口终点
    last_analyzed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    embedding            VECTOR(1536),                   -- 向量表示，用于相似度搜索
    version              INT         NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    ON COLUMN user_ai_profiles.confidence IS '画像可信度（0~1）';
COMMENT
    ON COLUMN user_ai_profiles.source_message_count IS '本次分析使用的消息条数';
COMMENT
    ON COLUMN user_ai_profiles.source_time_from IS '本次分析样本时间窗口起点';
COMMENT
    ON COLUMN user_ai_profiles.source_time_to IS '本次分析样本时间窗口终点';
COMMENT
    ON COLUMN user_ai_profiles.last_analyzed_at IS '最后一次画像分析完成时间';
COMMENT
    ON COLUMN user_ai_profiles.embedding IS '向量表示，用于相似度搜索、推荐等RAG功能';
CREATE UNIQUE INDEX idx_ai_profile_user_type ON user_ai_profiles (user_id, profile_type);
CREATE INDEX idx_ai_profile_analyzed_at ON user_ai_profiles (user_id, last_analyzed_at DESC);
CREATE INDEX idx_ai_profile_embedding ON user_ai_profiles USING hnsw (embedding vector_l2_ops) WHERE embedding IS NOT NULL;


-- 11. AI提供商配置表（ai_provider_configs）
CREATE TABLE ai_provider_configs
(
    id            BIGSERIAL PRIMARY KEY,
    provider_name VARCHAR(50) UNIQUE NOT NULL, -- openai, anthropic, azure, dashscope
    display_name  VARCHAR(100),
    is_enabled    BOOLEAN     DEFAULT TRUE,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);
COMMENT ON TABLE ai_provider_configs IS 'AI提供商主配置表';


-- 12. AI使用统计表（ai_usage_stats）
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
COMMENT ON TABLE ai_usage_stats IS 'AI使用统计表，用于用量分析';
CREATE INDEX idx_ai_usage_user_date ON ai_usage_stats (user_id, date);
CREATE INDEX idx_ai_usage_provider_date ON ai_usage_stats (provider, date);


-- 13. AI模型能力表（ai_model_capabilities）
CREATE TABLE ai_model_capabilities
(
    id              BIGSERIAL PRIMARY KEY,
    model_name      VARCHAR(100)      NOT NULL,
    provider        VARCHAR(50)       NOT NULL REFERENCES ai_provider_configs (provider_name),
    capability_type ai_task_type_enum NOT NULL,
    is_enabled      BOOLEAN     DEFAULT TRUE,
    is_default      BOOLEAN     DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
COMMENT ON TABLE ai_model_capabilities IS 'AI模型能力开关与默认路由表';
COMMENT ON COLUMN ai_model_capabilities.is_default IS '是否为该能力默认模型';
CREATE UNIQUE INDEX idx_model_capability_active
    ON ai_model_capabilities (provider, model_name, capability_type);

-- =======================================================================================
-- ==                                 END OF SCHEMA                                     ==
-- =======================================================================================

-- 插入默认提供商配置
INSERT INTO ai_provider_configs (provider_name, display_name, is_enabled)
VALUES ('openai', 'OpenAI', true),
       ('anthropic', 'Anthropic', true),
       ('dashscope', 'DashScope', true);