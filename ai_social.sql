-- =======================================================================================
-- ==                                   AI Social Hub                                 ==
-- ==                          Version: 2025-11-23                                    ==
-- =======================================================================================
-- Feature Include：
-- 1. 双主键：内部使用 BIGSERIAL，外部使用 UUID。
-- 2. 软删除：使用 'deleted_at' 字段以支持可恢复的数据，并配合性能优化的部分索引。
-- 3. 枚举类型：使用 ENUM 确保数据一致性。
-- 4. 面向未来的灵活性：'vip_level' 保持为 VARCHAR 以便扩展。
-- 5. 解耦的向量存储：单独的 'user_ai_vectors' 表，用于未来相似度搜索（RAG）。
-- 6. AI 任务追踪：专用 'ai_tasks' 表用于管理异步 AI 作业。
-- 7. AI 内容关联：增强的 'messages' 表用于将 AI 结果与原始内容关联。
-- 8. 模型版本管理：通过 'model_name' 字段支持平滑的模型升级。
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
CREATE TYPE ai_task_type_enum AS ENUM ('POLISH', 'SCHEDULE_EXTRACTION', 'PERSONA_ANALYSIS', 'SPEECH_TO_TEXT', 'CHAT_SUMMARY');
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


-- 5. 消息表 (messages)
CREATE TABLE messages
(
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    conversation_id   BIGINT      NOT NULL REFERENCES conversations (id),
    sender_id         BIGINT      NOT NULL REFERENCES users (id),
    content           TEXT,
    media_type        media_type_enum      DEFAULT 'TEXT',
    media_url         TEXT,
    parent_message_id BIGINT      REFERENCES messages (id) ON DELETE SET NULL,
    source_type       VARCHAR(30), -- 例如 'AI_POLISHED', 'SPEECH_TRANSCRIPT'
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ          DEFAULT NULL
);
COMMENT
    ON TABLE messages IS '消息表';
COMMENT
    ON COLUMN messages.parent_message_id IS '关联父消息ID，用于实现AI润色、语音转文字等功能';
COMMENT
    ON COLUMN messages.source_type IS '表明此消息的来源，用于UI区分展示';
CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_parent ON messages (parent_message_id);
CREATE INDEX idx_messages_active ON messages (id) WHERE deleted_at IS NULL;


-- 6. 日程事件表 (schedules)
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


-- =======================================================================================
-- ==                        AI, VECTOR & TASK-RELATED TABLES                           ==
-- =======================================================================================

-- 7. 异步AI任务追踪表 (ai_tasks)
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
    created_at        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
COMMENT
    ON TABLE ai_tasks IS '追踪所有异步AI任务的状态，用于前端展示与后端管理';
CREATE INDEX idx_ai_tasks_user_status ON ai_tasks (user_id, task_status);
CREATE INDEX idx_ai_tasks_type_status ON ai_tasks (task_type, task_status);


-- 8. 用户AI上下文存储表 (user_ai_contexts)
CREATE TABLE user_ai_contexts
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    context_type VARCHAR(30) NOT NULL,
    model_name   VARCHAR(50), -- 记录生成此上下文的模型版本
    content      JSONB       NOT NULL,
    version      INT         NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT
    ON TABLE user_ai_contexts IS '用户AI上下文存储（可读的原始数据），如性格画像、偏好等';
COMMENT
    ON COLUMN user_ai_contexts.model_name IS '生成此上下文的模型名称和版本，用于平滑升级';
CREATE UNIQUE INDEX idx_ai_context_user_type ON user_ai_contexts (user_id, context_type);


-- 9. 用户AI向量表 (user_ai_vectors) - 面向未来的设计
CREATE TABLE user_ai_vectors
(
    id           BIGSERIAL PRIMARY KEY,
    context_id   BIGINT UNIQUE NOT NULL REFERENCES user_ai_contexts (id) ON DELETE CASCADE,
    user_id      BIGINT        NOT NULL,
    context_type VARCHAR(30)   NOT NULL,
    model_name   VARCHAR(50)   NOT NULL,
    embedding    VECTOR(1536)  NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
COMMENT
    ON TABLE user_ai_vectors IS '用户AI向量表（不可读的数学表示），用于未来的相似度搜索、推荐、RAG等功能';
COMMENT
    ON COLUMN user_ai_vectors.embedding IS '向量维度取决于选择的Embedding模型（例如OpenAI text-embedding-3-small 是 1536）';
CREATE INDEX idx_ai_vectors_user_type ON user_ai_vectors (user_id, context_type);
CREATE INDEX idx_hnsw_embedding ON user_ai_vectors USING hnsw (embedding vector_l2_ops);

-- 10. 好友请求表 (friendship_requests)
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

-- 11. 好友关系表 (friendships)
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

-- =======================================================================================
-- ==                                 END OF SCHEMA                                     ==
-- =======================================================================================