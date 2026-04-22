# AI Social Hub - API Documentation

**Version:** 1.0 (Complete & Final)

**Base URL:** `https://api.ai-social.com/`

**API Prefix:** 由前后端配置统一提供（默认 `/api/v1`，后端对应 `server.servlet.context-path`）

> 文档中的接口路径均为**去前缀后的相对路径**（例如 `/ai/interactions/polish`）。
> 实际请求地址为：`<Base URL> + <API Prefix> + <Path>`。

**Last Updated:** 2026-04-22

**Architecture Overview:**

- **网关优先：** 所有请求通过 `ai-social-gateway` 路由，负责认证、限流和文件上传。
- **模块化服务：** 网关将请求分发到专用的下游服务（`identity`、`chat`、`schedule`、`ai-engine` 模块）。
- **AI 双模式：** 在线交互能力（润色、翻译、总结、智能回复）以 SSE 流式返回；离线/重任务通过 Redis Streams 异步队列处理。

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
  "email": "new_user@example.com",
  "phone": "13800138000"
}
```

**Response 201 (Created):**

```json
{
  "access_token": "<access_token>",
  "refresh_token": "<refresh_token>",
  "token_type": "Bearer",
  "expires_in": 900
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
  "access_token": "<access_token>",
  "refresh_token": "<refresh_token>",
  "token_type": "Bearer",
  "expires_in": 900
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
- `401 UNAUTHORIZED` (`TOKEN_INVALID`): Access Token 无效（格式错误、签名错误等）。
- `401 UNAUTHORIZED` (`REFRESH_TOKEN_INVALID`): Refresh Token 无效。
- `401 UNAUTHORIZED` (`TOKEN_BLACKLISTED`): Access Token 已被吊销（例如，在别处登出或刷新过）。
- `401 UNAUTHORIZED` (`REFRESH_TOKEN_REVOKED`): Refresh Token 已被吊销。

**客户端调用建议：**

- Access Token 有效期为 15 分钟，Refresh Token 有效期为 7 天。
- 推荐在收到后端返回 `401 UNAUTHORIZED` 且错误码为 `TOKEN_EXPIRED` 时，调用本接口。
- 每次调用成功后，客户端**必须**使用返回的**新 Refresh Token** 替换掉本地存储的旧 Refresh Token。
- 收到任何关于 Refresh Token 的错误（如 `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REVOKED`）时，前端应清除本地所有 Token
  并引导用户重新登录。

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
  "nickname": "Momo",
  "avatarUrl": "/uploads/avatars/new_avatar.jpg",
  "email": "new.email@example.com",
  "phone": "13800138001",
  "ai_analysis_enabled": true
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
此操作为异步触发：接口返回后会投递画像清理任务，后台再执行真实删除。

**Response 202 (Accepted):** 清理任务已受理。

---

### 2.5 `GET /users/{user_public_id}`

**查询指定用户资料**

**Path Parameters:**

- `user_public_id` (string, required): 用户公开 ID。

**Response 200 (Success):**

```json
{
  "public_id": "user_a1b2c3d4",
  "username": "Codetemp1",
  "nickname": "Copilot Coder",
  "avatar_url": "/uploads/avatars/default.png",
  "email": "codetemp1@example.com",
  "phone": "13800138000",
  "vip_level": "NORMAL",
  "ai_analysis_enabled": true
}
```

## 3.0 好友关系模块 (Friendship Service)

* **Implementation:** `ai-social-identity`
* **Description:** 负责管理用户之间的好友关系。

### 3.1 `GET /friends`

**获取当前用户的好友列表**

**Query Parameters:**

- `keyword` (string, optional): 按好友 `username` 或 `nickname` 模糊搜索。

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

**Response 204 (No Content):** 请求已发送。

### 3.3 `GET /friends/requests`

**获取好友请求列表（收到的和发出的）**

**Query Parameters:**

- `keyword` (string, optional): 按请求发起人昵称或 public_id 模糊搜索。

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

### 4.1.1 `GET /conversations`

**获取当前用户的会话列表（最近活跃优先）**

**Query Parameters:**

- `cursor` (long, optional): 游标（会话时间线锚点）。
- `limit` (int, optional, default: 200): 返回的最大会话数量，通常足以覆盖近期会话列表。

**Response 200 (Success):**

```json5
{
  "items": [
    {
      "public_id": "conv_a1b2c3d4",
      "type": "PRIVATE",
      "name": "Bob",
      "latest_message": {
        "public_id": "msg_latest_001",
        "content": "好的，明天见！",
        "created_at": "2025-11-23T10:00:00Z"
      },
      "unread_count": 3
    }
  ],
  "next_cursor": 1234,
  "has_more": true
}
```

### 4.1.2 `POST /conversations`

**创建或获取一个私聊会话**

**Request Body:**

```json
{
  "target_user_public_id": "user_u1v2w3e4"
}
```

**行为说明：**

- 如果当前用户与目标用户之间已存在 `type = "PRIVATE"` 的会话，则直接返回该会话；
- 否则新建一个 `type = "PRIVATE"` 的会话，并插入两条 `conversation_members` 记录。

**Response 200 (Success):**

```json
"conv_c1d2e3f4"
```

### 4.2.1 `GET /conversations/{conversation_public_id}/messages`

**游标分页获取消息历史（向上滑加载更多）**

**Query Parameters:**

- `before_message_id` (Long, optional): 上一页最早一条消息的 `id`；不传表示拉取最近消息。
- `limit` (int, optional, default: 50): 每次拉取的消息数量。

**Response 200 (Success):**

```json
{
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
      "content": "今天下午的会，你准备得怎么样了？",
      "media_type": "TEXT",
      "source_type": "SPEECH_TRANSCRIPT"
    }
  ],
  "next_cursor": 1234,
  "has_more": true
}
```

### 4.2.2 `POST /conversations/{conversation_public_id}/messages`

**发送消息**

**重要说明：**

- 所有 `public_id`（消息/文件）均由后端生成，客户端不应自行构造。
- 文件消息中的 `file_public_id` 不是“新建ID”，而是上传接口 `POST /files` 返回的文件标识，用于消息与已上传文件建立关联。

支持发送文本消息与文件消息：

- 文本消息：`media_type = TEXT`（默认）
- 文件消息：`media_type = FILE`，并传 `file_public_id`

**Request Body:**

```json
{
  "content": "明天的会议确认一下时间。",
  "media_type": "TEXT",
  "temp_id": "local-1715068800"
}
```

**文件消息 Request Body 示例：**

```json
{
  "content": "请看这份需求文档",
  "media_type": "FILE",
  "file_public_id": "file_f1a2b3c4",
  "temp_id": "local-1715068801"
}
```

**Response 201 (Created):**

```json
{
  "public_id": "msg_abc123",
  "sender": {
    "public_id": "user_me",
    "nickname": "我自己"
  },
  "content": "明天的会议确认一下时间。",
  "media_type": "TEXT",
  "media_url": null,
  "file": null,
  "source_type": null,
  "created_at": "2025-11-23T10:05:00Z"
}
```

**文件消息 Response 示例：**

```json
{
  "public_id": "msg_file_001",
  "sender": {
    "public_id": "user_me",
    "nickname": "我自己"
  },
  "content": "请看这份需求文档",
  "media_type": "FILE",
  "media_url": "https://cdn.ai-social.com/chat/group-001/req-v1.docx",
  "file": {
    "public_id": "file_f1a2b3c4",
    "original_filename": "req-v1.docx",
    "content_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size_bytes": 128430,
    "access_url": "https://cdn.ai-social.com/chat/group-001/req-v1.docx"
  },
  "source_type": null,
  "created_at": "2025-11-23T10:05:00Z"
}
```

**实时行为说明：**

- 服务器会在消息写入数据库后，异步地向会话中**其他在线成员**推送一条 `MESSAGE_CREATED` 事件（通过 SSE，详见 4.6）。
- 发送方可以：
    - 在点击发送时本地先做乐观更新（插入一条“发送中”的气泡）；
    - 收到本响应后，用返回的 `public_id` / 时间戳覆盖本地占位；
    - 也可以订阅 SSE，自身通过 `MESSAGE_CREATED` 事件确认送达。

---

### 4.2.3 `DELETE /conversations/{conversation_public_id}/messages`

**清空当前用户在该会话中的消息视图（软清空）**

**说明：**

- 该操作不会物理删除会话消息。
- 仅影响当前用户后续拉取消息时的可见范围。

**Response 204 (No Content):** 清空成功。

---

### 4.2.4 `POST /files`

**上传聊天文件（用于文件消息发送与后续文档总结）**

**说明：** 文件 `public_id` 由后端生成并在响应中返回；客户端只需在后续发送文件消息时回传该 `file_public_id`。

**Request:** `multipart/form-data`

- `file`: 二进制文件（必填）
- `conversation_public_id`: 会话公开 ID（可选，群聊文件建议传）

**Response 201 (Created):**

```json
{
  "public_id": "file_f1a2b3c4",
  "original_filename": "req-v1.docx",
  "content_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "size_bytes": 128430,
  "access_url": "https://cdn.ai-social.com/chat/group-001/req-v1.docx",
  "created_at": "2025-11-23T10:04:30Z"
}
```

---

### 4.2.5 `GET /files/{file_public_id}`

**获取聊天文件元数据**

**Response 200 (Success):**

```json
{
  "public_id": "file_f1a2b3c4",
  "original_filename": "req-v1.docx",
  "content_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "size_bytes": 128430,
  "access_url": "https://cdn.ai-social.com/chat/group-001/req-v1.docx",
  "created_at": "2025-11-23T10:04:30Z"
}
```

---

### 4.2.6 `POST /files/metadata/batch`

**批量获取文件摘要（用于消息分页后批量补齐 FILE 卡片信息）**

**Request Body:**

```json
{
  "file_public_ids": [
    "file_f1a2b3c4",
    "file_z9y8x7w6"
  ]
}
```

**Response 200 (Success):**

```json
[
  {
    "public_id": "file_f1a2b3c4",
    "original_filename": "req-v1.docx",
    "content_type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "size_bytes": 128430,
    "access_url": "https://cdn.ai-social.com/chat/group-001/req-v1.docx",
    "created_at": "2025-11-23T10:04:30Z"
  },
  {
    "public_id": "file_z9y8x7w6",
    "original_filename": "方案评审.pdf",
    "content_type": "application/pdf",
    "size_bytes": 523001,
    "access_url": "https://cdn.ai-social.com/chat/group-001/review.pdf",
    "created_at": "2025-11-24T08:31:10Z"
  }
]
```

---

## 群组管理 (Group APIs)

* **说明：** 群组在存储层复用 `conversations` 表（`type = 'GROUP'`）以及 `conversation_members` 表；本小节路由仅聚焦群资料和成员管理。

### 4.4.1 `POST /groups`

**创建新群组**

**Request Body:**

```json
{
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
  "public_id": "group_c1d2e3f4"
}
```

### 4.4.2 `PATCH /groups/{group_public_id}`

**修改群信息**

**Request Body:**

```json
{
  "name": "新的项目组名称"
}
```

**Response 200 (Success):** 返回更新后的群信息。

### 4.4.3 `POST /groups/{group_public_id}/members`

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

### 4.4.4 `GET /groups/{group_public_id}/members/admins`

**获取群管理员列表**
**Response 200 (Success):**

```json
[
  {
    "public_id": "user_e5f6g7h8",
    "nickname": "管理员A",
    "avatar_url": "/uploads/avatars/admin_a.jpg"
  }
]
```

### 4.4.5 `POST /groups/{group_public_id}/members/admins`

**设置群管理员**
**Request Body:**

```json
{
  "user_public_ids": [
    "user_e5f6g7h8",
    "user_f6g7h8i9"
  ]
}
```

**Response 204 (No Content):** 设置成功。

### 4.4.6 `DELETE /groups/{group_public_id}/members/admins`

**取消群管理员**
**Request Body:**

```json
{
  "user_public_ids": [
    "user_e5f6g7h8",
    "user_f6g7h8i9"
  ]
}
```

**Response 204 (No Content):** 取消成功。

### 4.4.7 `POST /groups/{group_public_id}/members/owner`

**转让群主**
**Request Body:**

```json
{
  "new_owner_public_id": "user_e5f6g7h8"
}
```

**Response 204 (No Content):** 转让成功。

### 4.4.8 `DELETE /groups/{group_public_id}/members/{user_public_id}`

**移出群成员**

**Response 204 (No Content):** 操作成功。

### 4.4.9 `DELETE /groups/{group_public_id}/members/me`

**退出群聊**

**Response 204 (No Content):** 退出群聊成功。

### 4.4.10 `DELETE /groups/{group_public_id}/members/owner`

**解散群聊**
*说明：仅群主可调用此接口，解散群聊将删除该群组的所有成员记录及会话记录。*

**Response 204 (No Content):** 群聊解散成功。

---

### 4.4.11 `GET /groups/{group_public_id}/members`

**获取群成员列表**

**Query Parameters:**

- `keyword` (string, optional): 按成员昵称模糊筛选。

**Response 200 (Success):**

```json
[
  {
    "public_id": "user_u1v2w3e4",
    "nickname": "张三",
    "role": "MEMBER",
    "joined_at": "2025-11-26T10:05:00Z"
  }
]
```

### 4.5.1 `GET /groups`

**获取当前用户所在的群组列表**

**Query Parameters:**

- `cursor` (long, optional): 游标（群组 ID 锚点）；
- `limit` (int, optional, default: 20): 每页数量，最大 100。
- `keyword` (string, optional): 按群名称模糊搜索。

**Response 200 (Success):**

```json
{
  "items": [
    {
      "public_id": "group_c1d2e3f4",
      "name": "新项目组",
      "member_count": 5,
      "created_at": "2025-11-26T10:00:00Z"
    }
  ],
  "next_cursor": 1234,
  "has_more": true
}
```

### 4.5.2 `GET /groups/{group_public_id}`

**获取单个群组详情**

**Response 200 (Success):**

```json
{
  "public_id": "group_c1d2e3f4",
  "name": "新项目组",
  "announcement": "本周五前完成接口联调。",
  "member_count": 5,
  "created_at": "2025-11-26T10:00:00Z",
  "members": [
    {
      "public_id": "user_u1v2w3e4",
      "nickname": "张三",
      "role": "ADMIN",
      "joined_at": "2025-11-26T10:05:00Z"
    }
  ]
}
```

### 4.6 实时事件 (SSE)

**Implementation:** `ai-social-chat`

**Description:** 通过 Server-Sent Events (SSE) 向在线客户端推送通知。

#### 4.6.1 `GET /sse/subscribe`

**建立 SSE 订阅连接**

- 建立与当前登录用户绑定的长连接。
- 服务端会自动为每条连接分配内部 `clientId` 并管理多端广播。

**Request:**

```http
GET /sse/subscribe HTTP/1.1
Accept: text/event-stream
Cookie: access_token=<access_token>
```

**Response 200:**

- Header: `Content-Type: text/event-stream;charset=UTF-8`
- Body: 持续事件流，事件体为统一 `Notification` 结构。

```json
{
  "type": "CONNECTION_ESTABLISHED",
  "payload": "SSE connection successful"
}
```

#### 4.6.2 事件：`CONNECTION_ESTABLISHED`

**触发时机：**

- SSE 连接建立成功后，服务端会立即推送该事件。

**事件格式：**

```json
{
  "type": "CONNECTION_ESTABLISHED",
  "payload": "SSE connection successful"
}
```

#### 4.6.3 事件：`MESSAGE_CREATED`

**触发时机：**

- 当任意用户发送消息成功后，服务端会向同会话其他成员推送。

**事件格式：**

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {
    "public_id": "msg_abc123",
    "sender": {
      "public_id": "user_bob",
      "nickname": "Bob"
    },
    "content": "明天的会议确认一下时间。",
    "media_type": "TEXT",
    "media_url": null,
    "source_type": null,
    "created_at": "2025-11-23T10:05:00Z"
  }
}
```

---

## 5.0 AI 引擎模块 (AI Engine Service)

* **Description:** 提供统一的 AI 能力，支持在线流式交互与异步任务处理两种模式。

### 5.1 `POST /ai/interactions/polish`

**Request Body (AI润色):**

```json
{
  "message": "我想要吃一个比较甜的食物可以吧",
  "model_option_code": "qwen3-max"
}
```

**Response**: 流式输出

```json
{
  "output": "我想吃一些甜的食物，可以吗？"
}
```

### 5.2 `POST /ai/interactions/schedule`

**Request Body (智能日程):**

```json
{
  "messages": [
    {
      "sender": "user1",
      "content": "@所有人 明天下午2点有个项目复盘会议，大家记得参加哦！",
      "timestamp": "2025-12-01T10:00:00Z"
    },
    {
      "sender": "user2",
      "content": "时间变更通知：由于天气原因，项目复盘会议改到后天下午2点。",
      "timestamp": "2025-12-01T11:00:00Z"
    }
  ],
  "model_option_code": "dashscope:qwen-max",
  "context": {
    "timezone": "Asia/Shanghai",
    "user_preferences": {
      "meeting_reminder_minutes": 30
    }
  }
}
```

**Response 200 (Success):**

```json
{
  "schedules": [
    {
      "title": "项目复盘会议",
      "time": "后天下午2点",
      "location": "会议室A",
      "participants": ["user1", "user2"]
    }
  ]
}
```

### 5.3 `GET /ai/jobs`

**获取AI任务列表状态和结果**

**Query Parameters:**

- `task_type` (string, optional): 任务类型过滤，如 `POLISH`, `SCHEDULE_EXTRACTION`, `PERSONA_ANALYSIS`。
- `status` (string, optional): 任务状态过滤，如 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`。
- `cursor` (long, optional): 游标（任务ID），用于翻页。
- `limit` (int, optional, default: 20, max: 100): 每页数量。

**Response 200 (Success):**

```json5
{
  "items": [
    {
      "public_id": "task_t1u2v3w4",
      "type": "SCHEDULE_EXTRACTION",
      "status": "COMPLETED",
      "requested_model_option_code": "dashscope:qwen-max",
      "resolved_model_name": "qwen-max",
      "resolved_provider": "dashscope",
      "created_at": "2025-12-01T10:00:00Z",
      "completed_at": "2025-12-01T10:00:05Z",
      "result": {
        "schedules": [
          {
            "title": "项目复盘会议",
            "start_time": "2025-12-05T14:00:00Z",
            "end_time": "2025-12-05T15:00:00Z",
            "attendees": [
              "user1",
              "user2"
            ],
            "location": "会议室A"
          }
        ]
      },
      "model_config": {
        "model_name": "gpt-4o",
        "temperature": 0.7
      }
    }
  ],
  "next_cursor": 12345,
  "has_more": false
}
```

### 5.4 `GET /ai/jobs/{public_id}`

**获取单个AI任务详情（用于异步任务轮询）**

**Response 200 (Success):**

```json
{
  "public_id": "task_p1q2r3s4",
  "type": "PERSONA_ANALYSIS",
  "status": "COMPLETED",
  "requested_model_option_code": "dashscope:qwen-max",
  "resolved_model_name": "qwen-max",
  "resolved_provider": "dashscope",
  "input_payload": {
    "messages": "...",
    "target_user_id": "user1"
  },
  "output_payload": {
    "analysis": {
      "personality": "理性",
      "traits": [
        "谨慎",
        "解决问题导向",
        "积极主动"
      ],
      "communication_style": "直接而建设性，善于提出具体建议。",
      "interests": []
    }
  },
  "error_message": null,
  "model_config": {
    "model_name": "gpt-4o",
    "temperature": 0.7
  },
  "token_usage": {
    "input_tokens": 150,
    "output_tokens": 200,
    "total_tokens": 350
  },
  "created_at": "2025-12-01T09:00:00Z",
  "completed_at": "2025-12-01T09:00:10Z"
}
```

### 5.4.1 AI 异步任务状态机

**状态定义：**

- `PENDING`: 任务已创建，等待消费者处理。
- `PROCESSING`: 任务正在执行中。
- `COMPLETED`: 任务执行成功，可读取 `output_payload`。
- `FAILED`: 任务执行失败，可读取 `error_message`。
- `CANCELLED`: 任务被取消（若启用取消能力）。

**状态迁移：**

```text
PENDING -> PROCESSING -> COMPLETED
PENDING -> PROCESSING -> FAILED
PENDING -> CANCELLED
PROCESSING -> CANCELLED (可选，取决于执行器是否支持中断)
```

**字段可见性规则：**

- `PENDING/PROCESSING`: `output_payload` 为空，`error_message` 为空。
- `COMPLETED`: `output_payload` 非空，`error_message` 为空。
- `FAILED`: `output_payload` 可为空，`error_message` 非空，并建议携带标准错误码前缀。
- `CANCELLED`: `output_payload` 为空，`error_message` 可选。

### 5.5 `GET /ai/jobs/message-candidates`

**获取可供用户显式选择的消息样本（用于 Custom Summary）**

**Query Parameters:**

- `conversation_public_id` (string, optional): 指定会话时，仅返回当前用户在该会话内的消息样本。
- `limit` (int, optional, default: 100): 候选消息条数上限。

**Response 200 (Success):**

```json
{
  "items": [
    {
      "sender": "张三",
      "content": "我建议先做灰度发布。",
      "timestamp": "2025-12-01T09:00:00Z"
    },
    {
      "sender": "张三",
      "content": "上线前要补充监控告警。",
      "timestamp": "2025-12-01T09:05:00Z"
    }
  ]
}
```

### 5.6 `POST /ai/interactions/smart-reply`

**Request Body (智能回复建议，支持两种模式):**

- 一体化模式：传 `message + conversation_public_id`，后端自动拉取最近 10 条上下文。
- 两步模式：前端自行准备 `conversation_history` 并传入。

```json
{
  "message": "明天的会议你参加吗？",
  "conversation_public_id": "conv_a1b2c3d4",
  "conversation_history": [
    {
      "sender": "user1",
      "content": "我们明天有个重要会议",
      "timestamp": "2025-12-01T08:00:00Z"
    }
  ],
  "user_profile": {
    "name": "张三",
    "role": "项目经理",
    "communication_style": "professional"
  },
  "model_option_code": "dashscope:qwen-max"
}
```

**Response 200 (Success, SSE流):**

`Content-Type: text/event-stream`

```text
data: {"output":"我会参加的"}
data: {"output":"，需要准备什么材料吗？"}
```

### 5.7 `POST /ai/interactions/summarize`

**Request Body (内容总结，支持两种模式):**

- 一体化模式：
- 传 `conversation_public_id`（后端自动取最近 50 条）
- 或传 `selected_message_ids`（后端按ID取消息并拼接内容）
- 两步模式：前端直接传 `content`

```json5
{
  // 模式1: 直接传 content（两步模式）
  "content": "今天的会议主要讨论了项目进展和下一步计划。有人认为需要加快开发进度，同时也要注意质量控制。最后决定在下周召开一次全体会议，确保所有成员都了解最新情况。",
  // 模式2: 仅传会话ID（后端自动取最近50条消息）
  "conversation_public_id": "conv_a1b2c3d4",
  // 模式3: 仅传消息ID（后端按ID查询）
  "selected_message_ids": [
    101,
    102,
    103
  ],
  "summary_type": "meeting",
  // meeting, chat, article, email
  "target_length": "short",
  // short, medium, long
  "keywords": [
    "重点",
    "决策",
    "行动项"
  ],
  "model_option_code": "dashscope:qwen-max"
}
```

**Response 200 (Success, SSE流):**

`Content-Type: text/event-stream`

```text
data: {"output":"会议主要内容总结..."}
data: {"output":"要点1: ..."}
```

### 5.8 `POST /ai/interactions/translate`

**Request Body (智能翻译):**

```json5
{
  "text": "Hello, how are you?",
  "source_language": "en",
  "target_language": "zh",
  "domain": "business",
  // business, casual, technical, academic
  "model_option_code": "dashscope:qwen-max"
}
```

**Response 200 (Success, SSE流):**

`Content-Type: text/event-stream`

```text
data: {"output":"你好"}
data: {"output":"，你怎么样？"}
```

### 5.9 `GET /ai/models`

**获取可用AI模型列表**

**Response 200 (Success):**

```json
{
  "models": [
    {
      "option_code": "dashscope:qwen-max",
      "display_name": "通义千问 Max",
      "name": "qwen-max",
      "provider": "dashscope",
      "capabilities": [
        "text",
        "reasoning"
      ]
    },
    {
      "option_code": "anthropic:claude-3-opus",
      "display_name": "Claude 3 Opus",
      "name": "claude-3-opus",
      "provider": "anthropic",
      "capabilities": [
        "text",
        "reasoning",
        "long_context"
      ]
    }
  ]
}
```

### 5.10 `GET /ai/profiles`

**获取用户AI画像**

**Query Parameters:**

- `profile_type` (string, optional): 画像类型，如 `PERSONA`, `PREFERENCES`。

**Response 200 (Success):**

```json
{
  "items": [
    {
      "profile_type": "PERSONA",
      "content": {
        "personality": "理性",
        "traits": [
          "谨慎",
          "解决问题导向"
        ]
      },
      "model_name": "qwen-max",
      "provider": "dashscope",
      "updated_at": "2025-12-01T10:00:00Z"
    }
  ]
}
```

### 5.11 `GET /ai/usage`

**获取AI使用统计**

**Query Parameters:**

- `date_from` (string, optional): 开始日期。
- `date_to` (string, optional): 结束日期。
- `provider` (string, optional): 提供商。

**Response 200 (Success):**

```json
{
  "summary": {
    "total_tokens": 15000,
    "input_tokens": 9000,
    "output_tokens": 6000,
    "total_cost": 0.25,
    "date_from": "2025-12-01",
    "date_to": "2025-12-31"
  },
  "daily_breakdown": [
    {
      "date": "2025-12-01",
      "tokens_used": 10000,
      "cost": 0.15
    }
  ],
  "by_provider": [
    {
      "provider": "openai",
      "tokens_used": 10000,
      "cost": 0.15
    }
  ]
}
```

---

## 6.0 日程模块 (Schedule Service)

* **Implementation:** `ai-social-schedule`
* **Description:** 负责日程的手动创建、管理，并处理由AI提取的日程。

### 6.1 `GET /schedules`

**获取指定时间范围内的日程列表**

**Query Parameters:**

- `start_time` (string, required, format: ISO8601) - 开始时间（包含）
- `end_time` (string, required, format: ISO8601) - 结束时间（包含）

**Response 200 (Success):**

```json
[
  {
    "public_id": "sch_s1t2u3v4",
    "title": "项目复盘会议",
    "description": "讨论上周项目进展",
    "start_time": "2025-12-05T14:00:00Z",
    "end_time": "2025-12-05T15:00:00Z",
    "location": "会议室A",
    "is_ai_extracted": true,
    "source_message_id": 12345
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
  "description": "周会",
  "start_time": "2025-12-05T14:00:00Z",
  "end_time": "2025-12-05T15:00:00Z",
  "location": "线上",
  "is_ai_extracted": false,
  "source_message_id": null
}
```

**Response 201 (Created):**

```json
{
  "public_id": "sch_s1t2u3v4",
  "title": "团队会议",
  "description": "周会",
  "start_time": "2025-12-05T14:00:00Z",
  "end_time": "2025-12-05T15:00:00Z",
  "location": "线上",
  "is_ai_extracted": false,
  "source_message_id": null
}
```

### 6.3 `PUT /schedules/{public_id}`

**更新日程信息**

**Request Body:**

```json
{
  "title": "更新后的会议标题",
  "description": "更新后的描述",
  "start_time": "2025-12-05T15:00:00Z",
  "end_time": "2025-12-05T16:00:00Z",
  "location": "会议室B"
}
```

**Response 200 (Success):**

```json
{
  "public_id": "sch_s1t2u3v4",
  "title": "更新后的会议标题",
  "description": "更新后的描述",
  "start_time": "2025-12-05T15:00:00Z",
  "end_time": "2025-12-05T16:00:00Z",
  "location": "会议室B",
  "is_ai_extracted": false,
  "source_message_id": null
}
```

### 6.4 `DELETE /schedules/{public_id}`

**删除日程**

**Response 204 (No Content):** 无返回内容。

---

## 7.0 网关与文件上传模块 (Gateway Service)

* **Implementation:** `ai-social-gateway`
* **Description:** 系统统一入口，提供文件上传等通用功能。

> 当前仓库中未包含 `POST /uploads` 的业务控制器实现；若网关后续接入该能力，请以网关模块实际实现为准。

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

## Appendix A: 统一错误响应模型

所有失败响应建议使用统一结构，便于前端和网关做通用处理。

**Error Response JSON:**

```json
{
  "code": 401,
  "msg": "Access Token 无效",
  "data": null,
  "errorCode": "TOKEN_INVALID",
  "traceId": "d2d97a60e6f544e0a59ee3d6c8d95d0f"
}
```

**字段说明：**

- `code`: HTTP 状态码。
- `msg`: 用户可读错误信息。
- `data`: 失败时固定为 `null`。
- `errorCode`: 稳定的业务错误码（用于前端分支判断与监控聚合）。
- `traceId`: 可选链路追踪标识（建议网关或服务端统一注入）。

## Appendix B: 错误码规范

| HTTP Status | Error Code                  | Description                                   |
|:------------|:----------------------------|:----------------------------------------------|
| 400         | `INVALID_INPUT`             | 请求参数无效或缺失。                                    |
| 401         | `AUTHENTICATION_REQUIRED`   | 用户未认证或登录态缺失。                                  |
| 401         | `BAD_CREDENTIALS`           | 用户名或密码错误。                                     |
| 401         | `TOKEN_EXPIRED`             | Access Token 已过期。客户端应使用 Refresh Token 来获取新令牌。 |
| 401         | `TOKEN_BLACKLISTED`         | Access Token 已被吊销（因为登出或刷新）。客户端应强制用户重新登录。      |
| 401         | `TOKEN_INVALID`             | Access Token 无效（格式、签名错误或无法解析）。客户端应强制用户重新登录。   |
| 401         | `REFRESH_TOKEN_INVALID`     | Refresh Token 无效或已过期。客户端应强制用户重新登录。            |
| 401         | `REFRESH_TOKEN_REVOKED`     | Refresh Token 已被系统吊销（可能因为安全风险）。客户端应强制用户重新登录。  |
| 403         | `ACCESS_DENIED`             | 无权访问该资源或执行该操作。                                |
| 404         | `RESOURCE_NOT_FOUND`        | 请求的资源不存在。                                     |
| 409         | `CONFLICT`                  | 资源冲突（如用户名已存在）。                                |
| 400         | `MODEL_OPTION_INVALID`      | 模型选项编码格式非法或无法识别。                              |
| 400         | `MODEL_NOT_ENABLED`         | 指定模型未启用或不可用。                                  |
| 400         | `MODEL_CAPABILITY_MISMATCH` | 指定模型不支持当前任务类型。                                |
| 429         | `RATE_LIMITED`              | 请求过于频繁，请稍后再试。                                 |
| 500         | `INTERNAL_ERROR`            | 服务器内部发生未知错误。                                  |
| 503         | `UPSTREAM_UNAVAILABLE`      | 上游依赖服务（如 AI 模型）暂时不可用。                         |

