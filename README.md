AI Social Hub — 项目说明

简介
----
AI Social Hub 是一个基于 Java 21 与 Spring Boot 的多模块 AI 平台样例，目标是提供可扩展的对话代理能力：融合短期会话窗口与长期记忆、可插拔的模型路由与工具调用（Agent 风格）、以及通过 Redis / PostgreSQL 持久化与扩展的工程化实现。

主要功能
----
- 实时对话交互与多轮智能回复（Agent 化）
- 会话记忆：短期窗口 + 长期归档（基于 Redis Stream 实现）
- 工具调用与多回合决策（ReAct-like 流程）
- 多模型路由与供应商抽象（可接入不同 LLM 提供商）
- 任务编排与落盘（ai_tasks 表；任务可追踪）

快速准备（先决条件）
----
- JDK 21
- Maven 3.8+
- Docker & Docker Compose（用于容器化部署）
- Redis 与 PostgreSQL（本地运行或容器）

部署指南：Docker（推荐用于测试/演示）
----
1. 在仓库根目录打开终端。
2. 进入应用模块目录（若存在 docker-compose 配置）：

```bash
cd ai-social-app
docker-compose up -d --build
```

3. 等待服务启动，检查容器日志：

```bash
docker-compose ps
docker-compose logs -f
```

说明：
- `ai-social-app/docker-compose.yaml` 默认会包含 Redis / Postgres / 应用容器的示例编排。若需自定义数据库密码、端口或 AI 提供商密钥，请编辑对应的 `.env` 或 `application.yaml` 环境变量。

本地运行（开发环境）
----
1. 启动外部依赖（Redis、PostgreSQL），可以使用本地服务或通过 Docker 快速启动：

```bash
# 在仓库根目录（或任意位置）运行一个临时的 redis 与 postgres
docker run -d --name tmp-redis -p 6379:6379 redis:7
docker run -d --name tmp-postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15
```

2. 使用 Maven 打包并运行应用：

```bash
# 在项目根目录
mvn clean package -DskipTests
# 直接运行打包后的主应用（示例）
java -jar ai-social-app/target/ai-social-app-1.0-SNAPSHOT.jar
```

3. 或者在开发时使用 Spring Boot 插件直接运行模块：

```bash
# 例如运行 ai-social-app 模块
mvn -pl ai-social-app spring-boot:run
```

配置说明
----
- 数据库：修改 `application.yaml` 或通过环境变量设置 `spring.datasource.url` / `username` / `password`。
- Redis：设置 `spring.redis.host` / `port` / `password`（如适用）。
- AI 提供商：在 `application.yaml` 或专用的 provider 配置中填入 API Key / Endpoint。
- 若使用 Docker Compose，可通过 `.env` 文件覆盖默认值。

整体架构概览
----
- 模块划分（核心模块）：
    - `ai-social-ai-engine`：AI 引擎核心，包含 Prompt 组装、Model 路由、Agent 记忆服务（Redis Stream 实现）与工具调用编排。
    - `ai-social-app`：应用启动器与整合（包含 docker-compose 示例）。
    - `ai-social-chat`：聊天相关接口/适配器。
    - `ai-social-common`：公共工具类、DTO 与基础设施封装（例如 JsonUtil、常量）。
    - 其它模块：`ai-social-gateway`、`ai-social-identity`、`ai-social-schedule` 等，承担网关、身份与定时任务功能。

- 运行时调用链（简化）：
    1. 外部请求 -> `AiInteractionController`
    2. 请求下发到 `AiOnlineInteractionService` -> `AiTaskOrchestrator`
    3. `AiFacadeHandler` 调度具体模型策略（`ModelProviderStrategy`）与 `AiToolRegistry` 工具
    4. 会话记忆由 `ConversationAgentMemoryService` 管理（近期 Stream + 归档摘要），被注入到 Prompt
    5. 模型产出与工具结果写回任务与记忆

代码及数据要点
----
- 会话记忆：使用 Redis Stream 保存近期轮次，定期触发归档并使用 LLM 做摘要压缩，归档写入 Redis String（带 TTL）。
- 持久化：业务任务与审计信息写入 PostgreSQL（`ai_tasks` 等表）。
- 模型扩展：通过 `ModelProviderStrategy` 抽象可以接入不同 LLM 提供商与多模型路由策略。

运行与验证
----
- 访问 API：默认情况下，应用启动后在 `http://localhost:8080`（或 `application.yaml` 中配置的端口）提供 REST 接口。
- 日志：使用容器或本地日志查看模型调用、工具调用与归档触发信息，定位问题时优先检查 `ai-social-ai-engine` 的日志输出。

目录结构（摘录）
----
```
ai-social-hub/
├─ ai-social-ai-engine/
├─ ai-social-app/
├─ ai-social-chat/
├─ ai-social-common/
├─ ai-social-gateway/
├─ ai-social-identity/
└─ docs/
```

贡献与联系
----
- 欢迎通过 Fork → PR 提交改进。请在 PR 描述中包含：改动目的、影响范围、兼容性说明与本地复现步骤。

常见问题（FAQ）
----
- Q: 启动失败提示无法连接 Redis / Postgres？
    - A: 检查 `application.yaml` 或环境变量，确认服务已运行且网络连通。使用 `telnet` / `nc` 或 `docker ps` 验证端口映射。
- Q: 如何更换 LLM 提供商？
    - A: 在 `ai-social-ai-engine` 的 provider 配置中添加/替换 provider，确保实现相应的 `ModelProviderStrategy` 并配置凭证。

更多细节
----
如需更详细的部署、运行日志或架构图，请告诉我你想要的侧重点（例如“生产部署示例”或“本地调试步骤”），我会补充到本文件或项目文档中。
