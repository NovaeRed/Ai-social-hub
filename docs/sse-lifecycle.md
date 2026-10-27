# SSE 生命周期、资源预算与通知契约

SSE 是数据库中聊天消息和 AI 任务结果的实时变更提示。`ENQUEUED` 只表示连接级队列接纳，服务端 `write.succeeded` 只表示发送调用成功；二者都不是客户端处理确认。页面重连握手后重新读取业务状态。

## 并发约束

`ConnectionRegistry` 拥有名额检查、替换和按连接对象移除的原子边界。网络关闭在边界外执行。替换连接仍然属于实例资源预算，直到旧 writer 和 emitter completion 都真正结束；不能靠删除注册记录制造空闲名额。

`EmitterContext` 拥有队列接受与关闭状态的一致性；三个优先级共享数量和字节上限。正常停机停止准入，进入 DRAINING，拒绝新事件并限时排空。传输断开立即 CLOSED，清空和统计剩余事件，不再向损坏的连接排水。writer 是唯一发送者，心跳与业务使用相同分类器。

事件类型显式映射至优先级，未知类型使用 NORMAL。队列事件是 JSON 快照；payload 在调用方后续变化不会改变已排队内容。总分发预算限制一次通知向全部连接等待空位的累计时间；它不是序列化大对象的 CPU 执行期限。

## 写入预算与真实释放

`chat.sse.push.write-stall-timeout-ms=0` 默认只观测，不能称为已启用慢写强制释放。开启时必须同时设置正数 `transport-write-timeout-ms`。独立共享巡检观察发送开始后的单调时间；它不依赖被阻塞的 writer。健康空闲不会因缺少业务通知被终止，心跳根据最近成功写入调度。

当前已适配的传输是项目默认的 Tomcat HTTP/1.1 NIO。`SseTomcatConfiguration` 将显式配置的 transport 预算设置到 connector 的 `connectionTimeout`，Tomcat 10.1.44 会将它用于初始 socket 的阻塞写超时。该配置作用于整个 connector，也影响请求读取；需要根据应用上传与网络基线校准。其他 connector 不应声称获得相同保障；开启 HTTP/2 或更换容器后必须重做停读测试。

应用巡检发现超过写入预算后会终止队列、移除注册并发起一次 emitter 结束。真正解除底层网络阻塞仍依赖容器超时。检测期限大致是应用预算加一次巡检间隔；资源释放还受传输超时和容器调度影响。`interrupt()`、Future 取消、emitter completion 调用都不独立作为释放证据。测试须在客户端仍打开且停止读取时，分别检查队列、writer、liveResources 和 Tomcat 连接数。

参考：[Tomcat 10.1.44 NioEndpoint 初始化写超时](https://github.com/apache/tomcat/blob/10.1.44/java/org/apache/tomcat/util/net/NioEndpoint.java)。部署前应复核使用版本。

## 指标与预警查询

启用 `spring.jmx.enabled=true` 后可通过应用本地 JMX 的 `cn.redture.sse:type=Connections`、`diagnostics` 操作查询；不新增开放 HTTP 管理接口。现有 JMX 认证和网络访问由部署配置控制。

快照包含注册连接、仍占资源的连接、writer、当前最长写入年龄、最老排队年龄、总排队字节、分层计数、耗时分桶和预警状态。诊断连接列表包含 clientId、userId、状态、最后成功写入墙上时间、队列容量及持续高占用年龄和首次关闭原因；这些标识不进入计数标签。首写尚未成功时，最后成功时间为 0，不伪记为建连时刻。

- `notification.*`：一次业务通知，离线也是一次处置。
- `enqueue.*`：每个目标连接各一次尝试，区分接纳、关闭和满载拒绝。
- `write.*`：业务和心跳发送调用，按原因区分失败。
- `handshake.*`：握手发送尝试、成功和失败；客户端侧握手/重连计数另由前端快照观察。
- `connection.closed.*`：每条连接唯一首次关闭原因。
- `event.abandoned`：关闭时尚未发送的排队事件，以及已出队但因关闭未能开始发送的事件；已尝试发送的失败另计 `write.failed.*`。
- `writer.started/exited`：实际 writer 生命周期。
- `warning.raised/recovered.*`：持续容量、排队延迟、写入停滞、终止后 writer 未退出、重要事件拒绝率和排队 P99 的预警及恢复。

耗时采用单调时间。`latency` 中 P99 为累计样本的分桶上界；`recent` 另提供十二个固定桶构成的滚动窗口，包括重要事件尝试数、拒绝数、排队样本数和排队 P99 上界。最老的部分桶可能被排除，因此窗口存在一个桶宽的误差，不能当作精确连续滑动分位数。

`warning-window-ms` 控制滚动窗口，`warning-min-samples` 为拒绝率和 P99 预警最低样本数，`warning-sustain-ms` 为持续时间。`critical-rejection-warning-ratio=0` 默认禁用拒绝率预警，正数时按 CRITICAL 连接级拒绝数/尝试数判断；`queue-delay-warning-ms=0` 默认禁用排队延迟预警。预警日志包含时间窗口、持续时间、资源分子分母、样本分子分母和 JMX 诊断入口，故障解除后记录恢复。

写入、排队阈值和持续时间应按业务基线配置；默认值不代表生产承载量或服务目标。没有接入短信、邮件或现有外部告警平台，JMX 与结构化日志供采集系统接入。前端 `getSubscriptionDiagnostics()` 和刷新任务的 `diagnostics()` 提供只读处理失败、并发、握手、重连及刷新合并统计，App 诊断日志附带快照。

## 配置

连接数量、队列数量、每事件字节、每连接队列字节及实例共享排队字节均有上限。字节按 JSON UTF-8 快照估算，表示排队数据预算，不等于 JVM 对象总占用或 socket 缓冲区占用。关键事件等待参数为 0 表示立即尝试，不存在无限排队等待。

旧 `max-slow-count` 配置现在明确为可恢复应用/格式失败的连续次数；只在成功写入后归零。旧 `slow-count-reset-interval-ms` 为兼容保留但不再定时清零。IO 传输不可用、对端断开、emitter 结束和写超时立即终止；正常离开不记录为系统 ERROR。

离线/满载/部分入队属于明确处置结果。确实需要“必须看见”的通知应另建持久化通知与客户端确认协议，不能用 SSE 内存队列或 Redis ACK 代替。

当前生产者尚无已读/输入状态通知及可靠的会话合并键，未猜测合并不同会话事件。增加这些事件时应先定义类型与会话键，再实现最新值合并及契约测试。
