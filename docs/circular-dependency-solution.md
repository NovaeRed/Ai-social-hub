# 循环依赖修复说明（方案A：事件驱动）

## 1. 背景与问题

在应用启动时出现以下 Bean 循环依赖：

- aiExternalServiceBridge -> userServiceImpl -> aiConfigServiceImpl -> aiExternalServiceBridge

根因是：

- identity/chat 模块通过同步调用直接依赖 ai-engine 的服务。
- ai-engine 又通过桥接层回调 identity/chat 能力。
- 同一 Spring 容器内形成双向注入闭环，Spring Boot 默认禁止循环引用，导致启动失败。

## 2. 目标

- 解除 identity/chat 对 ai-engine 服务层的直接同步依赖。
- 保持业务行为不变：
  - 用户 AI 授权开关变更仍触发 AI 画像策略。
  - 用户发消息仍触发画像时间线统计。
- 降低模块耦合，恢复单向依赖关系。

## 3. 改造方案

采用领域事件解耦：

- identity/chat 只负责发布事件。
- ai-engine 监听事件并执行原本的业务逻辑。
- 事件模型下沉到 common 模块，避免横向模块直接互相依赖。

## 4. 修改清单与位置

### 4.1 新增事件模型（common）

- 文件：ai-social-common/src/main/java/cn/redture/common/event/ai/AiAnalysisToggledEvent.java
  - 作用：承载用户 AI 授权状态变更（userId, enabled）。

- 文件：ai-social-common/src/main/java/cn/redture/common/event/ai/UserMessageCreatedEvent.java
  - 作用：承载用户发消息事件（userId, messageTime）。

- 文件：ai-social-common/src/main/java/cn/redture/common/event/ai/AiPersonaClearRequestedEvent.java
  - 作用：承载“用户请求清理 AI 画像”的事件（userId）。

### 4.1.1 新增桥接协议（common）

- 文件：ai-social-common/src/main/java/cn/redture/common/integration/ai/AiExternalService.java
  - 作用：统一 AI 引擎对外部域（identity/chat）的查询型端口定义。

- 文件：ai-social-common/src/main/java/cn/redture/common/integration/ai/dto/AiExternalMessageItem.java
  - 作用：统一桥接层消息 DTO，避免 app 依赖 ai-engine DTO。

### 4.2 identity 改为发布事件

- 文件：ai-social-identity/src/main/java/cn/redture/identity/service/impl/UserServiceImpl.java
- 变更点：
  - 移除 AiConfigService 注入。
  - 注入 ApplicationEventPublisher。
  - 在 updateUserInfo 中检测到 aiAnalysisEnabled 变更后，发布 AiAnalysisToggledEvent。

### 4.3 chat 改为发布事件

- 文件：ai-social-chat/src/main/java/cn/redture/chat/service/impl/MessageServiceImpl.java
- 变更点：
  - 移除 AiConfigService 注入。
  - 注入 ApplicationEventPublisher。
  - 在 createMessage 成功写入后，发布 UserMessageCreatedEvent（保留 try-catch，不影响消息主流程）。

### 4.4 ai-engine 增加事件监听器

- 文件：ai-social-ai-engine/src/main/java/cn/redture/aiEngine/listener/AiEngineDomainEventListener.java
- 变更点：
  - 监听 AiAnalysisToggledEvent，转调 aiConfigService.onAiAnalysisToggled。
  - 监听 UserMessageCreatedEvent，转调 aiConfigService.onUserMessageCreated。
  - 监听 AiPersonaClearRequestedEvent，转调 aiConfigService.clearPersonaByUserIdAsync。

### 4.5 app 桥接保持在聚合层，但迁移到 common 协议

- 文件：ai-social-app/src/main/java/cn/redture/app/bridge/AiExternalServiceBridge.java
- 变更点：
  - 继续保留在 app 聚合层（实现位置不变）。
  - 改为实现 common.integration.ai.AiExternalService。
  - 返回 common.integration.ai.dto.AiExternalMessageItem。

### 4.6 ai-engine 适配 common 协议

- 文件：ai-social-ai-engine/src/main/java/cn/redture/aiEngine/controller/AiTaskController.java
- 文件：ai-social-ai-engine/src/main/java/cn/redture/aiEngine/service/impl/AiInteractionServiceImpl.java
- 文件：ai-social-ai-engine/src/main/java/cn/redture/aiEngine/service/impl/AiConfigServiceImpl.java
- 变更点：
  - 改为注入 common.integration.ai.AiExternalService。
  - 在 AiInteractionServiceImpl 增加外部消息 DTO 到内部 MessageItem 的转换。
  - 删除 ai-engine 本地桥接接口文件，避免双协议并存。

### 4.7 模块依赖清理

- 文件：ai-social-chat/pom.xml
- 变更点：移除 ai-social-ai-engine 依赖。
- 说明：chat 通过 common 事件与 ai-engine 交互，不再需要直接依赖 ai-engine。

- 文件：ai-social-identity/pom.xml
- 变更点：移除 ai-social-ai-engine 依赖。
- 说明：identity 改为通过 common 事件触发 AI 清理/开关流程，不再直接依赖 ai-engine。

## 5. 修改后的业务逻辑

### 5.1 用户 AI 授权开关流程

1. 用户更新资料（identity）。
2. UserServiceImpl 检测 aiAnalysisEnabled 是否发生变化。
3. 若变化，发布 AiAnalysisToggledEvent。
4. ai-engine 监听器接收事件并调用 AiConfigService。
5. AiConfigService 执行画像清理/重置队列等策略。

### 5.2 用户发消息触发画像流程

1. 用户发送消息（chat），消息落库成功。
2. MessageServiceImpl 发布 UserMessageCreatedEvent。
3. ai-engine 监听器接收事件并调用 AiConfigService.onUserMessageCreated。
4. AiConfigService 执行时间线计数、冷却判断、达到阈值后投递画像任务。

### 5.3 用户手动清理画像流程

1. 用户调用 DELETE /users/me/ai-persona。
2. UserController 发布 AiPersonaClearRequestedEvent。
3. ai-engine 监听器接收事件并调用 AiConfigService.clearPersonaByUserIdAsync。
4. AI 清理任务异步入队并消费，不阻塞用户请求主流程。

## 6. 启动依赖关系变化

改造前（有环）：

- ai-engine -> app bridge -> identity/chat -> ai-engine

改造后（无环）：

- identity -> common(event)
- chat -> common(event)
- ai-engine -> common(event) + app bridge

桥接协议关系：

- ai-engine -> common.integration.ai（端口定义）
- app bridge -> common.integration.ai（端口实现） + identity/chat（领域服务）

identity/chat 不再直接依赖 ai-engine 服务 Bean，容器可正常拓扑排序。

## 7. 测试说明

### 7.1 编译验证

建议执行：

- mvn -pl ai-social-app -am -DskipTests compile

预期：编译成功，无 Bean 循环依赖相关构建异常。

### 7.2 启动验证

建议执行：

- 启动 ai-social-app 主应用。

预期：

- 不再出现 aiExternalServiceBridge / userServiceImpl / aiConfigServiceImpl 的循环依赖报错。

### 7.3 功能回归验证

- 用例A：用户开启/关闭 AI 画像授权
  - 操作：调用 PATCH /users/me 修改 aiAnalysisEnabled。
  - 预期：事件发布成功，ai-engine 对应策略执行（查看日志与任务队列）。

- 用例B：发送消息触发时间线
  - 操作：调用消息发送接口。
  - 预期：消息主流程正常返回；ai-engine 收到事件并更新时间线计数。

- 用例C：阈值与冷却
  - 操作：连续发送消息至阈值，并观察冷却窗口内重复触发行为。
  - 预期：达到阈值触发一次任务，冷却窗口内不重复触发。

- 用例D：手动清理画像
  - 操作：调用 DELETE /users/me/ai-persona。
  - 预期：identity 返回 202；ai-engine 监听事件并投递 AI_PERSONA_CLEAR 任务。

## 8. 风险与后续建议

- UserController 已改为发布事件，不再直接注入 AiConfigService。
- 若后续需要异步化，可将当前 Spring 事件替换为 MQ 事件（Kafka/RabbitMQ）以提升解耦与可观测性。
