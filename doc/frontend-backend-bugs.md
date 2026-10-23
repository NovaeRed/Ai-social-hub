# 前端与上线后端联调问题日志

## 本轮修复状态（独立 8083 实例）

以下原始条目保留复现历史，不代表仍全部未修复。集成分支为 `codex/ux-core-iteration`，原 8082 进程未替换；当前前端预览代理连接修复后的 8083。

| 条目 | 当前状态 |
| --- | --- |
| B01、B02、B03 | 已修复并真实验证：发送者、带消息群解散、群读取鉴权 |
| B06、B07、B09、B10 | 已修复并真实验证：任务详情、重新申请好友、私聊名称、群人数 |
| B11 | 空更新不执行 SQL，定向测试覆盖 |
| B04 | 已过滤不可用模型；用户配置密钥后，qwen-plus 认证及 5 项真实 AI 接口全部通过，并完成页面润色/日程保存验证 |
| B05 | 用户明确授权后已新增缺失表及索引；真实画像查询恢复 200 和正常空态 |
| B08、B12 | 原响应契约差异仍保留，前端继续兼容 HTTP/业务双状态及认证失败 |
| B13 | 前端注册后保存昵称，真实注册刷新已验证；后端注册原子保存建议仍保留 |

验收见 `iteration-acceptance.md`、`backend-core-fix-report.md`、`frontend-chat-fix-report.md` 与 `core-iteration-evidence.json`。

联调日期：2026-10-23（Asia/Shanghai）。目标服务：本机 8082，前缀 `/api/v1`。全部写入测试使用本次新建的 `webqa_…` 独立账号及其群聊；未更改现有业务账号、未直接写数据库或修改后端。PostgreSQL 使用 YAML 配置以只读事务核对；Redis 只执行 AUTH/PING，连接正常。凭证仅存于前端被 Git 忽略的 `.local` 目录，不写入本日志。

原始证据：

- `frontend-live-evidence.json`：第一轮业务与数据库核对。该轮普通成员改名的“失败”源于初始权限假设，已确认当前实现允许任意成员改名，见文末规则差异。
- `frontend-contract-regression.json`：文件、SSE、群权限、好友、Token 与 AI 接口回归。
- `frontend-edge-evidence.json`：群信息越权读取、成员计数、空管理员参数、有消息群解散，以及好友撤销/拒绝/接受。
- `frontend-ai-live-evidence.json`：按模型实际能力匹配后复测五项 AI 能力。

以下均已真实复现。前端测试通过表示正常操作及错误状态处理正确，不表示这些后端缺陷已经修复。

## B01 · 高：消息接口缺失 sender，无法识别发送方

接口：`POST /conversations/{id}/messages`、`GET /conversations/{id}/messages`，SSE 消息也使用相同转换结果。

复现：测试账号向群发文本，再获取历史。

```json
{"code":200,"data":{"items":[{"content":"用于验证含消息群聊的解散行为","public_id":"…","media_type":"TEXT","created_at":"…"}],"has_more":false}}
```

实际 `sender` 整段缺失；文档要求 `sender.public_id` 与 `sender.nickname`。根因线索：`MessageConverter` 仅映射消息实体，`MessageServiceImpl` 未补充发送者。

前端处理：当前用户发送成功的响应使用已知当前用户信息；历史缺失发送者时显示“未知成员”，不猜测归属。建议后端批量关联用户资料，为发送、历史及推送统一补齐 sender。

## B02 · 高：有历史消息的群无法解散

接口：`DELETE /groups/{id}/members/owner`。

复现：建群 → 发送一条文本消息 → 群主解散。实际 HTTP 500，`msg=服务器内部错误，请稍后重试`。同一接口对无消息空群返回成功。

服务日志：`update or delete on table "conversations" violates foreign key constraint "messages_conversation_id_fkey" on table "messages"`。`GroupServiceImpl.removeGroup` 直接删除会话，未处理消息等外键。

前端处理：失败时保留群空间和确认弹窗，明确报错。建议采用符合数据保留需求的软删除/归档方案，或完整且事务化地处理依赖，不能仅靠前端清空消息视图（该操作不会物理删除消息）绕过。

## B03 · 高：非成员可以读取群详情、成员和管理员

接口：`GET /groups/{id}`、`GET /groups/{id}/members`、`GET /groups/{id}/members/admins`。

复现：A 建群，只邀请 B；使用未入群 C 的令牌查询这三个接口。实际全部 HTTP/业务 200，返回群成员公开 ID、昵称、角色等。证据仅使用自建测试群，未探测他人群聊。

根因线索：`getGroupDetail`、`listGroupAdmins` 未校验当前用户 membership，`listGroupMembers` 复用前者。

前端处理：只从我的群列表进入；打开指定群 ID 时补充成员身份检查。此处理不构成服务端授权修复，后端必须统一校验成员身份并返回 403/404。

## B04 · 高：AI 模型目录与运行配置不一致，五项能力无法完成

`GET /ai/models` 返回可选项，但按能力选中并实际调用后：

| 能力 | 模型 | 实际错误 |
| --- | --- | --- |
| 润色 | `dashscope:qwen3-max-2026-01-23` | 缺少 DashScope apiKey 配置 |
| 智能回复 | 同上 | 缺少 DashScope apiKey 配置 |
| 日程提取 | 同上 | `TASK_EXECUTION_FAILED`，缺少 apiKey |
| 翻译 | `dashscope:qwen-turbo` | `MODEL_OPTION_INVALID`，未找到候选模型配置 |
| 总结 | `openai:gpt-4o` | `MODEL_OPTION_INVALID`，当前服务不支持 openai 提供商 |

真实服务当前只装配 DashScope 策略；数据库能力表和 YAML 中的候选项未统一。该问题不能靠改前端模型编码或模拟输出解决。

前端处理：按后端能力枚举筛选模型，配置默认候选，真实显示错误，支持取消请求。建议模型目录仅暴露实际已启用、供应商策略存在且必要配置齐备的模型，并由运维配置有效密钥。

## B05 · 高：AI 画像表未部署，查询和清理失败

接口：`GET /ai/profiles` 返回 HTTP 500；`DELETE /users/me/ai-persona` 受理后，后台清理任务同样失败。

数据库只读确认：存在 users、ai_tasks、ai_usage_stats、ai_model_capabilities 等表，但缺少 `user_ai_profiles`。服务日志明确记录 `relation "user_ai_profiles" does not exist`。

前端处理：画像页独立错误态，不影响任务/用量等页面；清理操作只提示“任务已受理”，不宣称清理成功。建议补齐受控数据库迁移及相关向量依赖，并回放失败任务。

## B06 · 中：任务详情的路径参数绑定失败

接口：`GET /ai/jobs/{publicId}`。先列出测试账号任务，再用列表返回的 ID 查询，返回 HTTP 500。

服务日志：`Name for argument of type [java.lang.String] not specified, and parameter name information not available via reflection. Ensure that the compiler uses the '-parameters' flag.`

代码线索：`AiJobController.getTaskDetail(@PathVariable String publicId)` 未显式指定参数名。建议使用 `@PathVariable("publicId")`，同时确保编译参数一致。

前端处理：任务详情失败显示错误，列表仍可使用。本次跨用户详情授权测试也被该 500 阻塞，不能据此断言授权正确或存在越权。

## B07 · 中：删除好友后，原方向无法重新申请

复现：A → C 申请 → C 接受 → A 删除 C → A 再向 C 发申请。

实际：好友列表中已没有 C，但发送请求仍返回业务 400“你们已经是好友”。反方向 C → A 申请、接受可以恢复关系。

根因线索：`FriendshipServiceImpl.sendFriendRequest` 在确认 friendship 不存在后，仍把历史请求 `ACCEPTED` 直接视作当前好友关系。建议历史 ACCEPTED 在当前关系不存在时允许重置请求或创建新的申请周期。

前端处理：保留真实错误提示，不伪造申请成功。

## B08 · 中：业务错误的 HTTP 状态固定为 200

复现：错误密码登录、空文本发送、非群主设管理员、非法模型等。实际 HTTP 200，响应体 `code` 分别为 401/400/403 等；成功创建/删除也常为 HTTP 200、body code=201/204。

根因线索：业务异常处理直接返回 `RestResult`，未设置响应状态；成功 Controller 也未设置 HTTP status。

前端处理：同时检查 HTTP 状态和 body code，兼容 `error_code`/`errorCode`，包括缺失 `data` 的错误体；业务 401 也支持 Token 刷新和登录态失效处理。建议统一通过 ResponseEntity 设置正确的 HTTP status。

## B09 · 中：私聊列表缺少对方信息和 name

接口：`GET /conversations` 返回 PRIVATE 项时只有 type、public_id、unread_count、latest_message，省略 name，也没有 peer public_id。

前端处理：从好友发起的会话使用已知昵称；无法确定对方身份的已有会话显示“私聊会话”，不依赖数组位置猜测。建议服务端关联另一位会话成员，补齐 name 和可选的对方用户资料。

## B10 · 中：群成员计数未包含群主

复现：A 建群并邀请 B，详情的 members 包含 A/B 两条，member_count 却为 1；只含群主的群可能为 0。邀请、移出时在该基础上增减，偏差持续。

根因线索：创建群时插入 OWNER 后未同步计数。前端在取得完整成员列表后按实际列表长度显示并更新当前群卡片；未打开群的列表摘要仍受服务端计数影响。建议初始化时含群主并校正历史计数，保持事务一致。

## B11 · 中：空管理员撤销参数导致 SQL 500

接口：`DELETE /groups/{id}/members/admins`，请求 `{"user_public_ids":[]}` 或 `[null]`。实际 HTTP 500，日志出现 SQL 末尾语法错误，预期参数校验 400。

前端处理：只能从已知实际成员发起单人撤销，禁止空提交。建议 DTO 校验非空列表、元素非空，并在服务层拦截空集合后再执行批量 SQL。

## B12 · 低：未登录响应不符合认证契约

`GET /users/me` 不带 Token 返回 HTTP 403，响应为空，而文档定义未认证为 401 和统一错误体。前端对此无 Token 的 403 同样引导登录；对于已登录用户的 403 保留为操作权限错误。

## 已确认的规则差异，不列为已证实缺陷

- 当前后端允许任意群成员修改群名及邀请新成员；前端按此展示。只有群主可设置管理员/转让/解散，管理员只能移出普通成员。若产品期望更严格规则，应先明确策略并同步后端。
- 后端支持创建只含群主的空群，前端已移除“必须先选好友”的限制。
- 群公告目前只有查询字段，Controller 明确标注修改公告待实现；前端展示公告，但不提供无法保存的编辑按钮。
- `/uploads` 在 API 文档中注明当前没有 Controller，头像只支持已存在的 URL/站内路径；聊天附件使用真实 `/files` 上传与鉴权下载。

## 复测方式

前端目录执行 `node scripts/live-audit.mjs`、`node scripts/contract-regression.mjs`、`node scripts/edge-cases.mjs`、`node scripts/ai-live-check.mjs`。这些脚本会在独立测试账号下创建联调数据，并更新证据 JSON；不要针对生产普通账号运行。令牌过期可重跑登录。

`npm test` 检查 HTTP/业务双状态、Token 轮换和 SSE 分片；`npx playwright test` 区分演示与真实后端项目，验证群成员管理、文件、登录、正常空群解散及已知后端错误态。后端修复后应同步修改当前明确验证错误态的用例，再要求正常闭环成功。

## B13 · 中：注册时未保存用户选择的显示名称

浏览器反馈发现首页显示 `AIHub_…`。代码核对：`RegisterRequestDTO` 没有 nickname 字段，`AuthServiceImpl.register` 固定生成昵称，原前端也未提供昵称输入，因此注册用户名未成为默认显示名称。

前端现已增加可选昵称，留空使用用户名；注册后调用 `PATCH /users/me` 保存选择。对历史系统生成昵称，仅在展示时回退到 username，保留自定义昵称。真实注册、刷新及 `/users/me` 返回值已验证昵称能保存。建议后端注册接口直接接受并校验 nickname，未提供时默认使用 username，保证注册与昵称保存的原子性。

## B14 · 中：陌生人缺少手机号与用户名精确查找

原接口只有 `GET /users/{user_public_id}`，同群联系人不是陌生人查找的替代方案。本轮新增 `GET /users/lookup?type=USERNAME|PHONE|PUBLIC_ID&value=...`，返回最小公开确认资料并按登录账号每分钟 10 次限流。前端默认按注册用户名查找，可切手机号或用户 ID，核对昵称后才提交申请。真实手机精确查询、申请、接受、删除和重新申请闭环通过，不展示查询结果中的内部编号或联系方式。

## B15 · 中：缺少会话偏好、逐条隐藏及批量转发持久化接口

当前仅支持整段清空当前用户的历史视图，没有置顶、从列表隐藏、按消息隐藏及原生批量转发接口。前端已实现明确标注的本机偏好及逐条转发；不能当成跨设备同步、撤回或批次原子操作。补充接口契约见 `backend-core-fix-report.md`。删除确认必须明确仅当前浏览器；附件转发重新鉴权下载并上传至接收会话，不直接跨会话复用原文件 ID。

## B16 · 高：任务详情缺少用户归属校验

修正 B06 后进一步发现详情服务仅按任务 public_id 读取，没有限定当前账号。已补用户归属检查，对不存在和他人任务统一 404；真实双账号调用和三项任务单元测试通过。内部任务查询方法仍供异步流程使用，公开详情入口统一鉴权。
