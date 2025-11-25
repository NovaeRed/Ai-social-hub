# AI Social Hub - API Documentation

**Version:** 1.0 (Complete & Final)

**Base URL:** `https://api.ai-social.com/`

**Last Updated:** 2025-11-23

**Architecture Overview:**

- **网关优先：** 所有请求通过 `ai-social-gateway` 路由，负责认证、限流和文件上传。
- **模块化服务：** 网关将请求分发到专用的下游服务（`identity`、`friendship`、`chat` 等）。
- **异步 AI：** 所有计算密集型的 AI 任务通过任务队列以异步方式处理，确保非阻塞的用户体验。

---

## 1.0 认证模块 (Identity Service)

* **Implementation:** `ai-social-identity`
* **Description:** 负责用户注册、登录、登出等身份认证操作。

### 1.1 `POST /auth/register`

**新用户注册**
创建新用户。为提供流畅体验，注册成功后将自动登录并返回Token。

**Request Body:**

```json
{
  "username": "new_user",
  "password": "a_strong_password_123",
  "email": "new_user@example.com"
}
```

**Response 201 (Created):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 1.2 `POST /auth/login`

**用户登录**
通过用户名和密码获取用于后续请求的JWT。

**Request Body:**

```json
{
  "username": "alice",
  "password": "mypassword123"
}
```

**Response 200 (Success):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 1.3 `POST /auth/logout`

**用户登出**
此操作将吊销当前会话，使 `access_token` 和 `refresh_token` 立即失效。

> **重要:** 无论 `access_token` 是否过期，客户端都应调用此接口来确保会话被彻底清除。

**Request Header:**

```
Authorization: Bearer <current_access_token_even_if_expired>
```

**Response 204 (No Content):** 成功登出，无返回内容。客户端收到此响应后，应立即清除本地存储的所有 Token。

### 1.4 `POST /auth/refresh`

**刷新访问令牌 (Access Token)**  
使用 Refresh Token 换取新的 Access Token，并实施令牌轮换（Token Rotation）策略以增强安全性。

> **安全机制 (Token Rotation):**
> - 客户端必须同时提供过期的 Access Token（在请求头）和有效的 Refresh Token（在请求体）。
> - 服务端会验证两个 Token 是否属于同一用户，并与 Redis 中存储的 Refresh Token 进行比对。
> - 验证成功后，会签发**一对全新的 Access Token 和 Refresh Token**，旧的 Refresh Token 将立即失效。
> - 如果服务端检测到已失效的 Refresh Token 被再次使用，会判定为令牌泄露，并立即吊销该用户的所有会话。

**Request Header:**

```
Authorization: Bearer <expired_access_token>
```

**Request Body:**

```json
{
  "refresh_token": "<refresh_token_string>"
}
```

**Response 200 (Success):**

```json
{
  "access_token": "<new_access_token>",
  "refresh_token": "<new_refresh_token>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Possible Error Responses:**

- `401 UNAUTHORIZED` (`TOKEN_EXPIRED`): Access Token 已过期。这是调用本接口的**预期**场景之一。
- `401 UNAUTHORIZED` (`INVALID_TOKEN`): Access Token 无效（格式错误、签名错误等）。
- `401 UNAUTHORIZED` (`INVALID_REFRESH_TOKEN`): Refresh Token 无效。
- `401 UNAUTHORIZED` (`TOKEN_BLACKLISTED`): Access Token 已被吊销（例如，在别处登出或刷新过）。
- `401 UNAUTHORIZED` (`REVOKED_REFRESH_TOKEN`): Refresh Token 已被吊销。

**客户端调用建议：**

- Access Token 有效期为 15 分钟，Refresh Token 有效期为 7 天。
- 推荐在收到后端返回 `401 UNAUTHORIZED` 且错误码为 `TOKEN_EXPIRED` 时，调用本接口。
- 每次调用成功后，客户端**必须**使用返回的**新 Refresh Token** 替换掉本地存储的旧 Refresh Token。
- 收到任何关于 Refresh Token 的错误（如 `INVALID_REFRESH_TOKEN`, `REVOKED_REFRESH_TOKEN`）时，前端应清除本地所有 Token 并引导用户重新登录。

---

## 2.0 用户模块 (Identity Service)

* **Implementation:** `ai-social-identity`
* **Description:** 负责管理用户个人资料、隐私设置和账户安全。

### 2.1 `GET /users/me`

**获取当前登录用户的信息**

**Response 200 (Success):**

```json
{
  "public_id": "user_a1b2c3d4",
  "username": "Codetemp1",
  "nickname": "Copilot Coder",
  "avatar_url": "/uploads/avatars/default.png",
  "email": "codetemp1@example.com",
  "ai_analysis_enabled": true
}
```

### 2.2 `PATCH /users/me`

**更新当前登录用户的信息**
此接口用于更新昵称、头像等，以及开启/关闭AI分析授权。

**Request Body (Example):**
*说明：只传递需要修改的字段。所有字段均为可选。*

```json
{
  "nickname": "Copilot Pro Coder",
  "avatarUrl": "/uploads/avatars/new_avatar.jpg",
  "email": "new.email@example.com",
  "phone": "13800138001",
  "aiAnalysisEnabled": true
}
```

**Response 200 (Success):** 返回更新后的完整用户信息。

### 2.3 `POST /users/me/password`

**修改当前登录用户的密码**

**Request Body:**

```json
{
  "current_password": "old_password_123",
  "new_password": "a_much_stronger_password_456"
}
```

**Response 204 (No Content):** 密码修改成功。

### 2.4 `DELETE /users/me/ai-persona`

**清除个人AI画像**
此操作将删除 `user_ai_contexts` 和 `user_ai_vectors` 表中与当前用户相关的所有记录。

**Response 204 (No Content):** 清除成功。

---

## 3.0 好友关系模块 (Friendship Service)

* **Implementation:** `ai-social-friendship`
* **Description:** 负责管理用户之间的好友关系。

### 3.1 `GET /friends`

**获取当前用户的好友列表**

**Response 200 (Success):**

```json
[
  {
    "public_id": "user_b2c3d4e5",
    "nickname": "Alice",
    "avatar_url": "/uploads/avatars/alice.jpg"
  }
]
```

### 3.2 `POST /friends/requests`

**发送好友请求**

**Request Body:**

```json
{
  "target_user_public_id": "user_c3d4e5f6",
  "message": "你好，我是xxx，想加你为好友。"
}
```

**Response 202 (Accepted):** 请求已发送。

### 3.3 `GET /friends/requests`

**获取好友请求列表（收到的和发出的）**

**Response 200 (Success):**

```json
{
  "incoming": [
    {
      "request_public_id": "freq_123abc",
      "sender": {
        "public_id": "user_d4e5f6g7",
        "nickname": "Bob"
      },
      "message": "Hey!",
      "status": "PENDING",
      "created_at": "2025-11-23T15:30:00Z"
    }
  ],
  "outgoing": []
}
```

### 3.4 `POST /friends/requests/{request_public_id}/accept`

**接受好友请求**

**Response 204 (No Content):** 成功添加好友。

### 3.5 `DELETE /friends/requests/{request_public_id}`

**拒绝或撤销好友请求**

**Response 204 (No Content):** 操作成功。

### 3.6 `DELETE /friends/{friend_public_id}`

**删除好友**

**Response 204 (No Content):** 成功删除好友。

---

## 4.0 聊天与群组模块 (Chat Service)

* **Implementation:** `ai-social-chat`
* **Description:** 负责会话、消息、群组管理，并与AI模块协作处理消息内的AI任务。

### 4.1 `GET /conversations`

**获取当前用户的会话列表**

**Response 200 (Success):**

```json
{
  "page": 1,
  "size": 20,
  "total_items": 2,
  "items": [
    {
      "public_id": "conv_a1b2c3d4",
      "type": "PRIVATE",
      "name": "Bob",
      "latest_message": {
        "content": "好的，明天见！",
        "created_at": "2025-11-23T10:00:00Z"
      }
    }
  ]
}
```

### 4.2 `POST /conversations`

**创建新会话**

**Request Body (Private Chat):**

```json
{
  "type": "PRIVATE",
  "target_user_public_id": "user_u1v2w3e4"
}
```

**Request Body (Group Chat):**

```json
{
  "type": "GROUP",
  "name": "新项目组",
  "member_public_ids": [
    "user_u1...",
    "user_v2..."
  ]
}
```

**Response 201 (Created):**

```json
{
  "public_id": "conv_c1d2e3f4"
}
```

### 4.3 `GET /conversations/{conversation_public_id}/messages`

**分页获取消息历史**

**Response 200 (Success):**

```json
{
  "page": 1,
  "size": 20,
  "total_items": 42,
  "items": [
    {
      "public_id": "msg_voice_123",
      "sender": {
        "public_id": "user_bob",
        "nickname": "Bob"
      },
      "content": null,
      "media_type": "VOICE",
      "media_url": "/uploads/voice/abc.mp3"
    },
    {
      "public_id": "msg_transcript_456",
      "sender": {
        "public_id": "system_ai",
        "nickname": "AI助手"
      },
      "content": "“今天下午的会，你准备得怎么样了？”",
      "media_type": "TEXT",
      "source_type": "SPEECH_TRANSCRIPT",
      "parent_message_public_id": "msg_voice_123"
    }
  ]
}
```

### 4.4 `POST /conversations/{conversation_public_id}/messages`

**发送消息**

**Request Body:**

```json
{
  "content": "明天的会议确认一下时间。",
  "media_type": "TEXT"
}
```

**Response 201 (Created):**

```json
{
  "public_id": "msg_q7r8s9t0"
}
```

### 4.5 `GET /conversations/{conversation_public_id}/stream`

**通过SSE接收实时消息**
此长连接用于接收新消息、AI任务状态更新等实时事件。

### 4.6 `PATCH /conversations/{conversation_public_id}`

**修改群信息**

**Request Body:**

```json
{
  "name": "新的项目组名称",
  "avatar_url": "/uploads/groups/new_group_avatar.png"
}
```

**Response 200 (Success):** 返回更新后的群信息。

### 4.7 `POST /conversations/{conversation_public_id}/members`

**邀请用户加入群聊**

**Request Body:**

```json
{
  "user_public_ids": [
    "user_e5f6g7h8",
    "user_f6g7h8i9"
  ]
}
```

**Response 204 (No Content):** 邀请成功。

### 4.8 `DELETE /conversations/{conversation_public_id}/members/{user_public_id}`

**移出群成员 或 主动退群**
*说明：当 `user_public_id` 为 `@me` 时，表示当前用户主动退出群聊。*

**Response 204 (No Content):** 操作成功。

---

## 5.0 AI 引擎模块 (AI Engine Service)

* **Description:** 提供统一的AI能力，通过异步任务接口进行交互。

### 5.1 `POST /ai/tasks`

**提交一个异步AI任务**

**Request Body (AI润色):**

```json
{
  "task_type": "POLISH",
  "source_message_public_id": "msg_draft_789"
}
```

**Request Body (智能日程提取 - 文本):**

```json
{
  "task_type": "SCHEDULE_EXTRACTION",
  "source_message_public_id": "msg_text_abc"
}
```

**Response 202 (Accepted):**

```json
{
  "task_public_id": "task_t1u2v3w4",
  "status": "PENDING"
}
```

### 5.2 `GET /ai/tasks/{task_public_id}`

**查询AI任务的状态和结果**

**Response 200 (Success, Completed - 智能日程):**

```json
{
  "public_id": "task_t1u2v3w4",
  "status": "COMPLETED",
  "output_payload": {
    "title": "项目复盘会议",
    "start_time": "2025-12-06T14:00:00Z",
    "end_time": "2025-12-06T15:00:00Z"
  }
}
```

---

## 6.0 日程模块 (Schedule Service)

* **Implementation:** `ai-social-schedule`
* **Description:** 负责日程的手动创建、管理，并处理由AI提取的日程。

### 6.1 `GET /schedules`

**获取指定时间范围内的日程列表**

**Query Parameters:**

- `start_date` (string, required, format: YYYY-MM-DD)
- `end_date` (string, required, format: YYYY-MM-DD)

**Response 200 (Success):**

```json
[
  {
    "public_id": "sch_s1t2u3v4",
    "title": "项目复盘会议",
    "start_time": "2025-12-05T14:00:00Z",
    "end_time": "2025-12-05T15:00:00Z",
    "is_ai_extracted": true
  }
]
```

### 6.2 `POST /schedules`

**手动创建日程**
*说明：AI提取的日程在用户确认后，也通过调用此接口创建。*

**Request Body:**

```json
{
  "title": "团队会议",
  "start_time": "2025-12-05T14:00:00Z",
  "end_time": "2025-12-05T15:00:00Z",
  "is_ai_extracted": false
}
```

**Response 201 (Created):**

```json
{
  "public_id": "sch_s1t2u3v4"
}
```

---

## 7.0 网关与文件上传模块 (Gateway Service)

* **Implementation:** `ai-social-gateway`
* **Description:** 系统统一入口，提供文件上传等通用功能。

### 7.1 `POST /uploads`

**上传媒体文件（如图片、语音）**
使用 `multipart/form-data` 格式。

**Response 201 (Created):**

```json
{
  "media_url": "/uploads/images/abc123xyz.jpg"
}
```

---

## Appendix A: 错误码规范

| HTTP Status | Error Code              | Description           |
|:------------|:------------------------|:----------------------|
| 400         | `INVALID_INPUT`         | 请求参数无效或缺失。            |
| 401         | `UNAUTHORIZED`          | 认证失败（如用户名密码错误）。         |
| 401         | `TOKEN_EXPIRED`         | Access Token 已过期。客户端应使用 Refresh Token 来获取新令牌。 |
| 401         | `TOKEN_BLACKLISTED`     | Access Token 已被吊销（因为登出或刷新）。客户端应强制用户重新登录。 |
| 401         | `INVALID_TOKEN`         | Access Token 无效（格式、签名错误或无法解析）。客户端应强制用户重新登录。 |
| 401         | `INVALID_REFRESH_TOKEN` | Refresh Token 无效或已过期。客户端应强制用户重新登录。 |
| 401         | `REVOKED_REFRESH_TOKEN` | Refresh Token 已被系统吊销（可能因为安全风险）。客户端应强制用户重新登录。 |
| 403         | `FORBIDDEN`             | 无权访问该资源或执行该操作。        |
| 404         | `NOT_FOUND`             | 请求的资源不存在。             |
| 409         | `CONFLICT`              | 资源冲突（如用户名已存在）。        |
| 429         | `RATE_LIMIT_EXCEEDED`   | 请求过于频繁，请稍后再试。         |
| 500         | `INTERNAL_SERVER_ERROR` | 服务器内部发生未知错误。          |
| 503         | `SERVICE_UNAVAILABLE`   | 依赖的第三方服务（如AI模型）暂时不可用。 |

## Appendix B: 技术实现注解

- **数据隔离**: 用户授权AI分析后，其聊天记录**仅用于在请求时动态构建上下文 (Prompt-Time Context)**，发送给大模型以生成个性化回复。数据
  **绝不会**被用于模型的再训练。
- **AI并发控制**: 为保证同一用户的AI请求按序处理并避免上下文冲突，所有提交的任务会根据 `user_id` 被放入一个**串行队列**
  中（如使用Redis List实现）。
- **模型路由**: `ai-social-ai-engine`内部会根据`task_type`进行**模型路由**，例如文本任务路由到`Qwen-Turbo`，多模态任务路由到
  `Qwen-VL-Max`，语音识别路由到`DashScope Paraformer`。
- **多端同步**: 通过SSE或WebSocket通道，当一个事件发生时（如新消息、AI任务完成），服务端向该用户所有在线设备广播事件，确保多端体验一致。
