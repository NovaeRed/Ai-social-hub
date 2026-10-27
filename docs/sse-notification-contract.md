# SSE 通知与 Redis ACK 契约

SSE 是持久化聊天消息、AI 任务结果的实时变更提示。数据库查询结果才是页面重进、重连后的状态来源。入队不等于网络写入、客户端收到或用户确认。无需为离线实时提示无限重试；必须由重连后的状态同步补齐页面。

`DispatchResult` 返回目标连接数、成功入队数、容量拒绝数和关闭拒绝数，所有计数非负且结果总数等于目标数。

| status | 含义 |
|---|---|
| NO_ACTIVE_CONNECTION | 快照中没有在线连接 |
| ENQUEUED | 所有目标连接均接受入队 |
| PARTIALLY_ENQUEUED | 至少一个目标接受，另有目标拒绝 |
| REJECTED_FULL | 无目标接受，存在容量拒绝；其余关闭拒绝仍保留计数 |
| REJECTED_CLOSED | 所有目标均因关闭拒绝 |

以上均是有效的实时提示处置结果，通知 handler 正常返回后消费者 ACK。ACK 表示任务处置完成，不承诺客户端送达。非预期异常向消费者传播，保留业务异常的错误码和原因，供重试策略判断。

重试不再先 ACK 再依赖进程内定时器：失败信封连同 `user_id`、`event_type`、`created_at_epoch`、`biz_id`、`trace_id`、`dlq_replay_count` 持久化至 Redis 排序集合，之后 ACK 原记录。共享定时任务每 100ms 最多提升 50 条到工作 Stream，进程重启后仍可恢复。提升先 XADD 再移除排序集合成员；转入 DLQ 和 DLQ 重放先 XADD 再 ACK 源记录。相关操作由 Lua 在 Redis 中串行执行。执行可能重复，不能用于推导 exactly-once。

重试预算耗尽后进入 DLQ；通知 DLQ 重放保留完整信封和累计重放次数，防止因丢失计数而无限循环。`ai.async.dlq.group` 可覆盖死信消费组，默认维持原组名。Redis Lua 涉及多个 key，当前实现面向项目单机 Redis；迁移 Redis Cluster 前须统一 hash slot。Redis 服务端自身持久化与复制策略不在此次测试范围内。

`AiNotificationRedisIT` 是显式开启的集成测试（`-Dsse.redis.it=true`），读取项目 `application.yaml` 与其导入的 `config/application-local.yaml`，使用随机键前缀、随机消费组和消费者。测试只逐个删除自身三个明确键，不读取业务队列。覆盖实际 Redis ACK、正常/离线/满载/部分/关闭结果、持久化重试与新 handler 恢复、信封保留、DLQ 重放限制、重试持久化故障保留 Pending。这里连接结果由 bridge 测试替身提供，SSE 实际队列并发由 chat 模块专项测试覆盖，不能称为完整浏览器至 Redis 端到端送达测试。

如果 Redis 连失败处置信封也无法持久化，源记录保持 Pending，不会提前 ACK。当前没有自动接管长时间运行任务的 XPENDING/XCLAIM 策略，恢复需由运维确认原消费者已停止后认领并重新处置。集成测试通过重新处理原 Pending 记录验证恢复路径；这不等于自动 Pending 恢复已经实现。不要用任意固定空闲阈值回收仍在运行的 AI 任务。

从项目根目录运行（需要项目 YAML 指向的测试 Redis 访问权限）：

```powershell
mvn -o -pl ai-social-ai-engine -am test '-Dtest=AiNotificationRedisIT' '-Dsse.redis.it=true' "-Dsse.project.root=$PWD" '-Dsurefire.failIfNoSpecifiedTests=false'
```
