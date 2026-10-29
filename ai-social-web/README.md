# 共鸣 · AI Social Hub 前端

Vue 3 + TypeScript + Pinia + Vite 中文社交工作台，依据 API 文档、Controller、实际响应和数据库结构实现。前端预览端口为 5186，后端地址通过 `API_PROXY_TARGET` 配置。

最新浅色、深色页面截图见[项目 README 页面预览](../README.md#页面预览)。

## 启动与配置

```powershell
cd ai-social-web
npm install
npm run dev -- --host 127.0.0.1 --port 5186 --strictPort
```

访问 <http://127.0.0.1:5186/>，使用后端账号登录或注册。注册密码至少 8 位，包含大小写字母、数字及 `@$!%*?&` 中的特殊字符，且只能包含这些字符与字母数字。

`.env.local` 被 Git 忽略；新环境可复制 `.env.example`。`VITE_DEMO_MODE=false` 使用真实 API，改为 `true` 可预览明确标记的内存演示数据；`API_PROXY_TARGET` 配置后端目标，`VITE_API_BASE` 配置 API 前缀，`VITE_DEFAULT_MODEL` 配置默认模型编码。改动后重启 Vite。

默认 API 前缀 `/api/v1`，开发代理匹配 `/api`；如后端变更该前缀，请同步调整 `vite.config.ts`。生产运行 `npm run build`，部署 `dist` 并为 API 配置反向代理，Vite 开发代理不在生产生效。页面使用 hash 路由。

## 开发规范与代码结构

前端采用真实路由懒加载，App.vue 仅保留布局入口。跨页面会话状态由 Pinia 管理，局部群空间与 AI 活动状态由页面调用的 composables 管理，不放入全局 Store。

| 目录            | 职责                                                                  |
| --------------- | --------------------------------------------------------------------- |
| src/views       | 登录、概览、消息、好友、群聊、日程、AI 助手、AI 活动和设置页面        |
| src/layouts     | 侧栏、顶部导航、路由内容和公共提示                                    |
| src/components  | 聊天操作、附件、日期输入和弹窗等组件                                  |
| src/composables | 联系人查找、日历与编辑、AI 工具、实时连接、群管理、活动查询与焦点管理 |
| src/stores      | 会话级共享状态和跨页面业务协调                                        |
| src/services    | HTTP、认证刷新、SSE 与文件下载的统一请求实现                          |
| src/types       | 明确的业务模型和分页响应类型                                          |
| src/constants   | 导航和 AI 工具等固定配置                                              |
| src/styles      | 通过 scoped 样式入口加载的组件专用样式                                |

使用 PascalCase 组件文件、camelCase 函数、带 is/has/can 前缀的布尔状态和 UPPER_SNAKE_CASE 常量。公共基础样式及设计令牌保留在 src/style.css，登录、日历、账户和群空间样式使用 scoped 隔离。src/lib/api.ts 仅保留旧导入路径兼容，新代码从 src/services/http.ts 导入请求。路由守卫仅改善登录体验，业务权限仍由后端校验。

提交前执行：

```bash
npm run lint
npm run format:check
npm run build
npm test
npx playwright test --project demo
```

ESLint 检查 Vue 模板、未使用声明、显式 any、console 和 debugger；TypeScript 使用 strict 模式，Prettier 统一为 2 空格和 100 字符建议行宽。提交前检查共享状态归属、重复请求保护、异步过期响应隔离以及卸载时的连接/定时器清理。

## 页面与功能

| 页面     | 已实现内容                                                                                                                      |
| -------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 我的概览 | 未读统计、好友统计、今日日程、AI 任务与快捷入口                                                                                 |
| 消息     | 全部/私聊/群聊筛选、搜索、历史游标分页、文本发送、文件上传、鉴权下载、语音消息播放、AI 润色/总结入口、个人消息视图清空          |
| 好友     | 用户名/手机号/用户 ID 精确查找、按昵称选择同群联系人、好友资料、申请、收到/发出申请筛选、接受/拒绝/撤销、删除好友、私聊入口     |
| 群聊     | 独立群空间、搜索与分页、空群或选好友创建、详情、成员搜索、邀请、移出、管理员设置/撤销、群主转让、退出与解散、角色对应的操作入口 |
| 日程     | 月历、按日查看、手动创建、修改与删除、AI 来源标记                                                                               |
| AI 助手  | 按能力筛选模型、润色/回复/总结/翻译、SSE 输出、停止生成、结果带回会话、从个人消息选择总结样本、日程提取与准确时间确认后保存     |
| AI 活动  | 任务类型/状态筛选、游标分页、详情、处理中任务定时刷新、日期/提供商用量统计、画像类型查询和独立错误态                            |
| 个人设置 | 昵称/邮箱、AI 授权、手机号、已有头像 URL、修改密码、异步清理画像                                                                |
| 登录注册 | 真实登录注册、登出、登录态保护、Token 轮换                                                                                      |

群公告支持群主编辑和清除（最多 2000 字），普通成员和管理员只读；后端同时校验群主权限。群详情独占内容区，返回群列表后再切换其他群。头像支持已有 URL，后端尚无文档中 `/uploads` 的实现，聊天文件使用实际 `/files`。

## 已对齐的响应差异

接口层同时识别 HTTP 状态与 `RestResult.code`，兼容错误体省略 `data`、`error_code/errorCode`、204 空响应。Token 使用 sessionStorage；并发过期请求共用一次轮换，普通请求、AI 流和 SSE 均携带 Bearer。SSE 断线自动退避重连。

历史消息 sender 缺失时显示“未知成员”，当前用户发送的响应可依据已知当前用户补足。缺失名称的旧私聊显示“私聊会话”，从好友发起时使用已知昵称。群详情按实际完整成员列表显示人数。

以上属于前端容错，不代表服务端错误均已修复。真实环境还需要正确初始化数据库、配置供应商密钥与候选模型；页面保留实际错误状态，不用演示结果替代。历史联调报告已移除，当前行为应以代码和针对目标环境重新运行的测试为准。

## 测试

```powershell
npm test
npm run build
npx playwright test
```

单元测试覆盖响应包装、HTTP 200 业务错误、并发 Token 轮换、中文 SSE 分片和流式鉴权。Playwright 自动准备 5190 演示服务及 5186 真实服务（已有服务可复用），使用本机 Microsoft Edge。演示测试覆盖各页 375/768/1024/1440 宽度，真实测试覆盖群生命周期、角色、文件上传下载、刷新持久化、登录保护及已知后端失败状态。截图位于被忽略的 `artifacts/`。

真实测试需要先执行以下初始化脚本，创建独立 `webqa_…` 测试账号。凭证只保存在被忽略的 `.local/`，不提交版本库。以下脚本会通过接口产生测试账号/群聊/消息等数据，应仅用于获授权的测试环境：

```powershell
node scripts/live-audit.mjs
node scripts/contract-regression.mjs
node scripts/edge-cases.mjs
node scripts/ai-live-check.mjs
```

数据库只读核对脚本从后端 YAML 读取连接配置，只获取表清单和聚合计数；Redis 仅验证认证与 PING。结果写入被 Git 忽略的 `ai-social-web/artifacts/integration/`，不含令牌、口令或密钥。接口回归证据中保留未通过项，不能以浏览器错误处理测试通过来代替后端修复。

`scripts/core-iteration-check.mjs` 用于核心接口回归，`scripts/provider-smoke-check.mjs` 用于供应商连通性检查，`scripts/seed-test-community.mjs` 用于显式初始化测试社区；这些工具的报告也写入上述目录。种子脚本要求设置 `SEED_PASSWORD`，仅在获授权的测试环境运行。

## 素材与代码

公共组件位于 `src/components/`，业务页面位于 `src/views/`，可复用逻辑位于 `src/composables/`。统一请求位于 `src/services/http.ts`，业务类型位于 `src/types/domain.ts`，`src/lib/api.ts` 保留旧导入兼容。

主题插画：`public/images/social-hero.png`，使用内置 imagegen 工具生成；提示词：

> Wide hero illustration for a Chinese AI social communication dashboard. Charming editorial 3D clay sculpture of coral orange and lavender speech bubbles, cream sparkle, sage green sphere and connecting curved ribbon on a pale peach background. Soft studio shadows, tactile matte material. Subjects at right, empty left space for HTML heading. No text or watermark.

品牌图标：原创 `public/favicon.svg`；功能图标：Lucide。

## 浏览器反馈修正

注册可填写昵称，留空时使用用户名。当前后端注册接口会自动生成昵称，前端在注册成功后通过资料更新接口保存用户选择；若更新失败，会进入个人设置保留待保存昵称，避免重复注册。旧账号仍使用系统生成昵称时，界面回退显示注册用户名，用户自定义昵称优先保留。

手机端点击侧边栏之外的遮罩或按 Escape 可收起菜单。设置页不再展示、复制当前账号的公开 ID。

群详情、好友资料和任务详情同样不再展示或复制内部编号；缺失昵称、会话名称时使用普通名称。任务结果与沟通画像递归移除关联 ID 字段，错误提示隐藏技术编号。正常聊天消息及用户自己填写的内容保持原文。

群详情通过浏览器历史状态保存选择，地址栏使用 `/groups`，兼容旧群链接并在打开后移除 ID 参数，刷新仍能恢复详情。添加好友默认使用本轮新增的 `/users/lookup` 按用户名或手机号精确查找，核对昵称后才申请；用户 ID 保留现有精确查询兼容。同群联系人在独立选项卡提供，不替代陌生人查找。

## 聊天内 AI 交互

消息页在桌面及手机宽度均保留左右布局：左侧会话列表可独立滚动，右侧展示当前聊天。会话项显示未读角标，收到 SSE 消息后刷新，打开并读取会话后清零。

聊天里的润色、回复建议和对话总结在当前聊天内完成，不跳转 AI 工作台。润色成功后填入编辑框，可撤销；回复建议需要点击使用；总结在会话内展开并可复制。生成失败保留原文，切换会话或离开消息页会取消未完成请求，避免结果写入另一段会话。聊天中不展示模型配置，独立 AI 工作台仍可从导航主动进入。

交互验证包含 375/580/1024 像素下的左右布局、未读角标、原地结果、失败保留和取消隔离。真实接口测试以双账号发送消息验证 SSE 未读数；模型成功流和失败流通过浏览器响应模拟验证，不能据此认定后端 AI 配置问题已经修复。

## 界面主题

顶部太阳/月亮按钮可以快速切换浅色与深色；个人设置的“外观”提供浅色、深色和跟随系统三种模式，默认跟随系统。主题保存在当前浏览器的 `gongming-theme` 本地存储中，多标签页同步；禁用本地存储时仍可切换当前页面主题。

主题在应用挂载前初始化，登录页也使用当前主题。颜色由 CSS 变量控制，覆盖导航、聊天气泡、日历、表单、弹窗、错误提示与 AI 面板，并适配原生表单控件和键盘焦点。自动化回归覆盖主题跟随、手动覆盖、刷新保持、多标签同步和手机/桌面布局。

## 文件与表单交互

选择聊天附件后先展示文件名和大小，可移除或重新选择，点击“发送文件”才开始上传。发送失败保留附件供重试，已成功上传的文件复用上传结果，消息重试复用临时编号，发送期间禁止重复提交。空文件和超过后端 1 GB 限制的文件在本地拦截。

联系方式、密码及画像操作的错误提示分别显示在所属表单或确认弹窗中。日程使用统一的文本时间输入和日期选择器，保留键盘编辑及日期有效性校验，避免浏览器原生分段时间输入的蓝色选中样式。切换月份只加载日程接口，保留页面布局和滚动位置；快速切换时忽略较早请求的结果。
