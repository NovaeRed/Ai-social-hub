# AI Social Hub - API Documentation

**Version:** 1.0 (Complete & Final)

**Base URL:** `https://api.ai-social.com/`

**Last Updated:** 2025-11-23

**Architecture Overview:**

- **网关优先：** 所有请求通过 `ai-social-gateway` 路由，负责认证、限流和文件上传。
- **模块化服务：** 网关将请求分发到专用的下游服务（`identity`、`chat`、`schedule`、`ai-engine` 模块）。
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
  "email": "new_user@example.com",
  "phone": "13800138000"
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
- 收到任何关于 Refresh Token 的错误（如 `INVALID_REFRESH_TOKEN`, `REVOKED_REFRESH_TOKEN`）时，前端应清除本地所有 Token
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

* **Implementation:** `ai-social-identity`
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

### 4.1.1 `GET /conversations`

**获取当前用户的会话列表（最近活跃优先）**

**Query Parameters:**

- `cursor` (string, optional): 预留的游标参数，当前实现通常一次性返回最近会话，客户端可忽略。
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
      "member_count": 10,
      // 仅群组会话返回
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

**Response 201 (Created):**

```json
{
  "public_id": "conv_c1d2e3f4"
}
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
      "source_type": "SPEECH_TRANSCRIPT",
      "parent_message_public_id": "msg_voice_123"
    }
  ],
  "next_cursor": {
    "before_message_id": 1234
  },
  "has_more": true
}
```

### 4.2.2 `POST /conversations/{conversation_public_id}/messages`

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
  "public_id": "msg_abc123",
  "sender": {
    "public_id": "user_me",
    "nickname": "我自己"
  },
  "content": "明天的会议确认一下时间。",
  "media_type": "TEXT",
  "media_url": null,
  "source_type": null,
  "parent_message_public_id": null,
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

### 4.3.1 `POST /conversations/typing`

**Request Body**

```json
{
  "conversation_public_id": 1234,
  "target_user_public_id": 1234
}
```

**上报当前用户在指定会话中的“正在输入”状态**

**行为说明：**

- 仅当目标会话为**私聊会话**（`type = "PRIVATE"`）时才会生效，群组会话不会产生任何实时事件；
- 客户端在用户开始输入时，按一定频率（例如每 500ms 以上）调用此接口即可，无需在停止输入时额外上报；
- 服务端不落库，只在内存或缓存中短暂记录状态，并通过 SSE 向会话内**其他成员**广播 `TYPING` 事件（事件类型为 `TYPING`，详见
  4.6.3），当前上报用户自身不会收到该事件；
- 前端可以在收到一条 `TYPING` 事件后，显示“对方正在输入...”，并在若干秒内未再收到新的 `TYPING` 事件时自动隐藏提示。

**Response 204 (No Content):** 上报成功。

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

### 4.5.1 `GET /groups`

**获取当前用户所在的群组列表**

**Query Parameters:**

- `cursor` (string, optional): 游标，用于翻页，后端可基于 `conversations.id` 或 `created_at` 实现；
- `limit` (int, optional, default: 20): 每页数量，最大 100。

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
  "member_count": 5,
  "created_at": "2025-11-26T10:00:00Z",
  "members": [
    {
      "public_id": "user_u1v2w3e4",
      "nickname": "张三",
      "is_admin": true,
      "joined_at": "2025-11-26T10:05:00Z"
    }
  ]
}
```

### 4.6 实时事件 (SSE)

**Implementation:** `ai-social-chat`

**Description:** 通过 Server-Sent Events (SSE) 将新消息、正在输入等事件实时推送给在线客户端。

#### 4.6.1 `GET /api/v1/sse/subscribe`

**建立 SSE 订阅连接**

- 建立一个与当前登录用户绑定的 SSE 长连接，用于接收后台推送的各种通知事件。
- 需要携带正常的认证 Header（`Authorization: Bearer <access_token>`）。

**Request:**

```http
GET /api/v1/sse/subscribe HTTP/1.1
Accept: text/event-stream
Authorization: Bearer <access_token>
```

**Response 200:**

- Header: `Content-Type: text/event-stream;charset=UTF-8`
- Body: 持续的 SSE 事件流，每条事件的数据部分是一个 JSON 对象：

```json5
{
  "type": "MESSAGE_CREATED",
  "payload": {
    // ...
  }
}
```

**客户端接入示例（浏览器）：**

```js
const es = new EventSource('/api/v1/sse/subscribe', {withCredentials: true});

es.onmessage = (event) => {
    const notification = JSON.parse(event.data);
    switch (notification.type) {
        case 'MESSAGE_CREATED':
            handleMessageCreated(notification.payload);
            break;
        case 'TYPING':
            handleTyping(notification.payload);
            break;
        default:
            console.warn('Unknown SSE event type', notification.type);
    }
};
```

#### 4.6.2 事件：`MESSAGE_CREATED`

**触发时机：**

- 当任意用户调用 `POST /conversations/{conversation_public_id}/messages` 发送新消息且写入成功后，
- 服务器会异步地向该会话中所有**其他成员**推送一条 `MESSAGE_CREATED` 事件。

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
    "parent_message_public_id": null,
    "created_at": "2025-11-23T10:05:00Z"
  }
}
```

> 注：`payload` 的结构与 `GET /conversations/{conversation_public_id}/messages` 返回的 `MessageItemVO`
> 完全一致，前端可直接复用同一套类型定义。

**前端建议处理逻辑：**

- **聊天窗口：**
    - 如果 `payload` 所属会话正是当前打开的会话：
        - 将该消息 append 到本地消息数组末尾；
        - 如果当前处于底部，可自动滚动到底部 / 播放提示音。
- **会话列表：**
    - 在本地找到对应会话条目，更新：
        - `latest_message` 字段为该条消息的 `public_id` / `content` / `created_at`；
        - 若该会话当前不在前台聊天窗口，则 `unread_count += 1`；
    - 将该会话移动到列表顶部，以模拟微信的“最近会话置顶”效果。

--- 

#### 4.6.3 事件：`TYPING`

**适用范围：**

- 仅在私聊会话中生效（`type = "PRIVATE"`）。群组会话调用 `POST /conversations/typing` 将不会产生任何
  SSE 事件。

**触发时机：**

- 当某个用户在私聊会话中调用 `POST /conversations/typing` 上报输入状态（`is_typing = true/false`
  ）时；
- 服务端会向该会话中的**另一方用户**推送 `TYPING` 事件，上报方自己不会收到该事件。

**事件格式：**

```json
{
  "type": "TYPING",
  "payload": {
    "conversation_public_id": "conv_a1b2c3d4",
    "user_id": 123
  }
}
```

**前端建议处理逻辑：**

- 在对应会话窗口中，根据 `payload.is_typing` 显示或隐藏对方的“正在输入...”指示；
- 可以对同一用户、同一会话的 `TYPING` 事件做简单节流/去抖，避免频繁刷新 UI。

---

### 4.6.4 事件：`STOP_TYPING`

**适用范围：**

- 仅在私聊会话中生效（`type = "PRIVATE"`）。群组会话调用 `DELETE /conversations/typing` 将不会产生任何
  SSE 事件。

**触发时机：**

- 当某个用户在私聊会话中调用 `DELETE /conversations/typing` 上报停止输入状态时；
- 服务端会向该会话中的**另一方用户**推送 `STOP_TYPING` 事件，上报方自己不会收到该事件。

**事件格式：**

```json
{
  "type": "STOP_TYPING",
  "payload": {
    "conversation_public_id": "conv_a1b2c3d4",
    "user_id": 123
  }
}
```

---

## 5.0 AI 引擎模块 (AI Engine Service)

* **Description:** 提供统一的AI能力，支持多模型配置和流式输出，通过异步任务接口进行交互。

### 5.1 `POST /api/v1/ai/tasks/polish`

**Request Body (AI润色):**

```json
{
  "message": "我想要吃一个比较甜的食物可以吧",
  "override_config": {
    "temperature": 0.8,
    "max_tokens": 1500
  }
}
```

**Response**: 流式输出

```json
{
  "output": "我想吃一些甜的食物，可以吗？"
}
```

### 5.2 `POST /api/v1/ai/tasks/schedule`

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
  "override_config": {
    "temperature": 0.3,
    "max_tokens": 500
  },
  "context": {
    "timezone": "Asia/Shanghai",
    "user_preferences": {
      "meeting_reminder_minutes": 30
    }
  }
}
```

**Response 202 (Accepted):**

```json
{
  "task_public_id": "task_t1u2v3w4"
}
```

### 5.3 `GET /api/v1/ai/tasks`

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

### 5.4 `GET /api/v1/ai/tasks/{public_id}`

**获取单个AI任务详情（用于异步任务轮询）**

**Response 200 (Success):**

```json
{
  "public_id": "task_p1q2r3s4",
  "type": "PERSONA_ANALYSIS",
  "status": "COMPLETED",
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

### 5.5 `POST /api/v1/ai/tasks/personality-analysis`

**Request Body (性格分析):**

```json5
{
  "messages": [
    {
      "sender": "user1",
      "content": "我觉得这个方案不太可行，风险太大了。",
      "timestamp": "2025-12-01T09:00:00Z"
    },
    {
      "sender": "user1",
      "content": "不过我们可以考虑一些风险控制措施。",
      "timestamp": "2025-12-01T09:05:00Z"
    },
    {
      "sender": "user2",
      "content": "是的，我同意你的看法。",
      "timestamp": "2025-12-01T09:10:00Z"
    }
  ],
  "target_user_id": "user1",
  "analysis_config": {
    "dimensions": [
      "openness",
      "conscientiousness",
      "extraversion",
      "agreeableness",
      "neuroticism"
    ],
    "depth": "comprehensive"
    // basic, standard, comprehensive
  },
  "override_config": {
    "temperature": 0.5,
    "max_tokens": 2000
  }
}
```

**Response 202 (Accepted):**

```json
{
  "task_public_id": "task_p1q2r3s4"
}
```

### 5.6 `POST /api/v1/ai/tasks/smart-reply`

**Request Body (智能回复建议):**

```json
{
  "message": "明天的会议你参加吗？",
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
  "override_config": {
    "temperature": 0.7,
    "max_tokens": 500
  }
}
```

**Response 200 (Success):**

```json
{
  "output": [
    "我会参加的，需要准备什么材料吗？",
    "好的，会议几点开始？",
    "我可能需要请假，有其他安排。"
  ]
}
```

### 5.7 `POST /api/v1/ai/tasks/summarize`

**Request Body (内容总结):**

```json5
{
  "content": "今天的会议主要讨论了项目进展和下一步计划。有人认为需要加快开发进度，同时也要注意质量控制。最后决定在下周召开一次全体会议，确保所有成员都了解最新情况。",
  "summary_type": "meeting",
  // meeting, chat, article, email
  "target_length": "short",
  // short, medium, long
  "keywords": [
    "重点",
    "决策",
    "行动项"
  ],
  "override_config": {
    "temperature": 0.3,
    "max_tokens": 1000
  }
}
```

**Response 200 (Success):**

```json
{
  "summary": "会议主要内容总结...",
  "key_points": [
    "要点1",
    "要点2",
    "要点3"
  ],
  "action_items": [
    "行动项1",
    "行动项2"
  ]
}
```

### 5.8 `POST /api/v1/ai/tasks/translate`

**Request Body (智能翻译):**

```json5
{
  "text": "Hello, how are you?",
  "source_lang": "en",
  "target_lang": "zh",
  "domain": "business",
  // business, casual, technical, academic
  "override_config": {
    "temperature": 0.1,
    "max_tokens": 1000
  }
}
```

**Response 200 (Success):**

```json
{
  "output": "你好，你怎么样？"
}
```

### 5.9 `GET /api/v1/ai/models`

**获取可用AI模型列表**

**Response 200 (Success):**

```json
{
  "models": [
    {
      "name": "gpt-4o",
      "provider": "openai",
      "capabilities": [
        "text",
        "image",
        "code",
        "reasoning"
      ],
      "pricing": {
        "input_token_price": 0.005,
        "output_token_price": 0.015
      },
      "max_tokens": 128000
    },
    {
      "name": "claude-3-opus",
      "provider": "anthropic",
      "capabilities": [
        "text",
        "reasoning",
        "long_context"
      ],
      "pricing": {
        "input_token_price": 0.015,
        "output_token_price": 0.075
      },
      "max_tokens": 200000
    }
  ]
}
```

### 5.10 `POST /api/v1/ai/config`

**设置用户AI配置**

开启 AI 画像分析功能后，系统不会自动读取历史消息。用户需要通过 `/api/v1/ai/profiles/init` 接口手动触发首次画像生成。

**Request Body:**

```json
{
  "default_model": "gpt-4o",
  "preferences": {
    "temperature": 0.7,
    "max_tokens": 1000,
    "auto_moderation": true,
    "smart_reply_enabled": true
  }
}
```

**Response 200 (Success):**

```json
{
  "message": "配置已保存",
  "config_id": "conf_123"
}
```

### 5.11 `GET /api/v1/ai/config`

**获取用户AI配置**

**Response 200 (Success):**

```json
{
  "default_model": "gpt-4o",
  "preferences": {
    "temperature": 0.7,
    "max_tokens": 1000,
    "auto_moderation": true,
    "smart_reply_enabled": true
  }
}
```

### 5.12 `GET /api/v1/ai/profiles`

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
      "updated_at": "2025-12-01T10:00:00Z"
    }
  ]
}
```

### 5.13 `POST /api/v1/ai/profiles/init`

**手动触发 AI 画像初始化**

基于用户最近的聊天记录生成性格画像。此操作需要用户在设置中已开启 AI 画像分析功能。

**Response 200 (Success):**

```json
{
  "message": "画像初始化任务已启动"
}
```

### 5.14 `GET /api/v1/ai/usage`

**获取AI使用统计**

**Query Parameters:**

- `date_from` (string, optional): 开始日期。
- `date_to` (string, optional): 结束日期。
- `provider` (string, optional): 提供商。

**Response 200 (Success):**

```json
{
  "total_tokens": 15000,
  "total_cost": 0.25,
  "usage_by_model": [
    {
      "model_name": "gpt-4o",
      "tokens": 10000,
      "cost": 0.15
    }
  ]
}
```

**Response 200 (Success):**

```json
{
  "message": "配置已保存",
  "config_id": "config_abc123"
}
```

### 5.10 `GET /ai/config`

**获取用户AI配置**

**Response 200 (Success):**

```json
{
  "default_model": "gpt-4o",
  "providers": [
    "openai",
    "anthropic"
  ],
  "preferences": {
    "temperature": 0.7,
    "max_tokens": 1000,
    "auto_moderation": true,
    "smart_reply_enabled": true
  },
  "usage": {
    "daily_tokens": 15000,
    "monthly_tokens": 450000,
    "cost": 25.50
  }
}
```

### 5.11 `GET /ai/profiles`

**获取用户AI画像**

**Query Parameters:**

- `profile_type` (string, optional): 配置类型过滤，如 `PERSONA`, `PREFERENCES`, `BEHAVIOR`。

**Response 200 (Success):**

```json
{
  "items": [
    {
      "profile_type": "PERSONA",
      "content": {
        "big_five": {
          "openness": 0.8,
          "conscientiousness": 0.7,
          "extraversion": 0.6,
          "agreeableness": 0.9,
          "neuroticism": 0.4
        },
        "communication_style": "collaborative",
        "interests": [
          "technology",
          "business",
          "innovation"
        ]
      },
      "model_name": "gpt-4o",
      "provider": "openai",
      "created_at": "2025-12-01T10:00:00Z",
      "updated_at": "2025-12-01T10:00:00Z"
    }
  ]
}
```

### 5.12 `GET /ai/usage`

**获取AI使用统计**

**Query Parameters:**

- `date_from` (string, optional): 开始日期，格式 YYYY-MM-DD
- `date_to` (string, optional): 结束日期，格式 YYYY-MM-DD
- `provider` (string, optional): 服务提供商过滤

**Response 200 (Success):**

```json
{
  "summary": {
    "total_tokens": 450000,
    "input_tokens": 300000,
    "output_tokens": 150000,
    "total_cost": 25.50,
    "date_from": "2025-12-01",
    "date_to": "2025-12-31"
  },
  "daily_breakdown": [
    {
      "date": "2025-12-01",
      "tokens_used": 15000,
      "cost": 0.85
    }
  ],
  "by_provider": [
    {
      "provider": "openai",
      "tokens_used": 300000,
      "cost": 18.00
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
  "title": "团队会议"
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
  "title": "更新后的会议标题"
}
```

### 6.4 `DELETE /schedules/{public_id}`

**删除日程**

**Response 204 (No Content):** 无返回内容。

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

| HTTP Status | Error Code              | Description                                   |
|:------------|:------------------------|:----------------------------------------------|
| 400         | `INVALID_INPUT`         | 请求参数无效或缺失。                                    |
| 401         | `UNAUTHORIZED`          | 认证失败（如用户名密码错误）。                               |
| 401         | `TOKEN_EXPIRED`         | Access Token 已过期。客户端应使用 Refresh Token 来获取新令牌。 |
| 401         | `TOKEN_BLACKLISTED`     | Access Token 已被吊销（因为登出或刷新）。客户端应强制用户重新登录。      |
| 401         | `INVALID_TOKEN`         | Access Token 无效（格式、签名错误或无法解析）。客户端应强制用户重新登录。   |
| 401         | `INVALID_REFRESH_TOKEN` | Refresh Token 无效或已过期。客户端应强制用户重新登录。            |
| 401         | `REVOKED_REFRESH_TOKEN` | Refresh Token 已被系统吊销（可能因为安全风险）。客户端应强制用户重新登录。  |
| 403         | `FORBIDDEN`             | 无权访问该资源或执行该操作。                                |
| 404         | `NOT_FOUND`             | 请求的资源不存在。                                     |
| 409         | `CONFLICT`              | 资源冲突（如用户名已存在）。                                |
| 429         | `RATE_LIMIT_EXCEEDED`   | 请求过于频繁，请稍后再试。                                 |
| 500         | `INTERNAL_SERVER_ERROR` | 服务器内部发生未知错误。                                  |
| 503         | `SERVICE_UNAVAILABLE`   | 依赖的第三方服务（如AI模型）暂时不可用。                         |

## Appendix B: 技术实现注解

- **数据隔离**: 用户授权AI分析后，其聊天记录**仅用于在请求时动态构建上下文 (Prompt-Time Context)**，发送给大模型以生成个性化回复。数据
  **绝不会**被用于模型的再训练。
- **AI并发控制**: 为保证同一用户的AI请求按序处理并避免上下文冲突，所有提交的任务会根据 `user_id` 被放入一个**串行队列**
  中（如使用Redis List实现）。
- **模型路由**: `ai-social-ai-engine`内部会根据`task_type`进行**模型路由**，例如文本任务路由到`Qwen-Turbo`，多模态任务路由到
  `Qwen-VL-Max`，语音识别路由到`DashScope Paraformer`。
- **多端同步**: 通过SSE或WebSocket通道，当一个事件发生时（如新消息、AI任务完成），服务端向该用户所有在线设备广播事件，确保多端体验一致。
