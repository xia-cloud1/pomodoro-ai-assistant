# 番茄钟 · AI 效率分析系统

[![CI](https://github.com/xia-cloud1/pomodoro-ai-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/xia-cloud1/pomodoro-ai-assistant/actions)

> 一个「专注计时 + 任务管理 + 数据统计 + AI 分析」的一站式效率工具。
> 后端 Spring Boot 3 / JDK 25，前端 uni-app（Vue 3），前后端分离，可打包为 H5 / App / 小程序。

---

## 一、项目介绍

### 背景

市面上的番茄钟大多只是「倒计时器」：时间到了打个卡，但**不知道时间花在了哪个任务上、这周效率是升是降、问题出在哪里**。考研、写论文、实习备考这类需要长期投入的场景，缺的不是计时工具，而是**可量化的反馈**。

### 业务目标

把「专注」这件事变成一条可追踪的数据链路：

1. **专注**：服务端记录每一次番茄的开始、完成、中断，防止客户端作弊。
2. **归因**：番茄可以绑定任务，统计每个任务的实际投入，对比预估与实际的偏差。
3. **反馈**：日/周/月统计、效率得分、热力图、连续打卡、积分等级、成就与排行榜。
4. **洞察**：接入 DeepSeek，根据真实专注数据生成日报/周报/月报，并支持效率问答与任务拆分。

### 项目角色

个人独立完成：需求拆解、数据库设计、后端接口、前端页面、联调测试、Docker 部署与文档维护。

---

## 二、技术选型

| 层次 | 技术 | 选择理由 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.5.16 | 生态成熟，适合快速实现 REST API 与 AOP 能力 |
| 运行环境 | JDK 25 (LTS) | 使用新 LTS，验证新版本兼容性 |
| ORM | MyBatis-Plus 3.5.12 | 单表 CRUD 简洁，分页插件开箱即用，复杂查询仍可写 SQL |
| 数据库 | MySQL 8.0 | 事务 + 索引，满足统计类查询 |
| 缓存 | Redis 7（可选） | 存放 refresh token 白名单；**不可用时自动降级，不影响主流程** |
| 认证 | JWT（jjwt 0.12.5）+ BCrypt | 无状态鉴权，双令牌（access + refresh） |
| AI | DeepSeek API（OpenAI 兼容协议） | 成本低；未配置 Key 时自动降级 |
| 前端 | uni-app + Vue 3 | 一套代码同时支持 H5 / App / 小程序 |
| 前端工具 | HBuilderX | 免 Node 构建链，直接运行与真机调试 |
| 部署 | Docker 多阶段构建 + Docker Compose | 一条命令拉起 MySQL + Redis + 应用 |
| 可观测性 | Actuator + Micrometer + Prometheus + Grafana | 健康检查、JVM/HTTP/业务指标、预置可视化面板 |
| 测试 | JUnit 5 + MockMvc | 真实 Spring 上下文 + 数据库的集成测试 |

**关键设计约定**：HTTP 状态码恒为 200，业务状态放在响应体 `code` 中（200 成功 / 400 参数错误 / 401 未登录 / 404 不存在 / 409 冲突 / 429 限流）。前端只需判断一次 `body.code`。

---

## 三、系统架构图（文字描述）

```
┌─────────────────────────────────────────────────────────┐
│ 客户端层：uni-app（Vue 3）                                │
│ 专注页 / 任务页 / 统计页 / 我的页 / AI 页 / 排行榜 / 海报  │
│ 统一 request 封装：token 注入、401 单飞刷新、i18n、暗黑模式 │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP + JSON（Bearer Token）
┌──────────────────────────▼──────────────────────────────┐
│ 接入层                                                    │
│ TraceIdFilter（traceId → MDC + 响应头 X-Trace-Id）         │
│ UserContextFilter（ThreadLocal 清理）                     │
│ JwtInterceptor（登录校验 / 用户存在性校验）                │
│ AOP：WebLogAspect 请求日志 / @RateLimit 限流              │
│      @Idempotent 幂等                                     │
└──────────────────────────┬──────────────────────────────┘
┌──────────────────────────▼──────────────────────────────┐
│ 业务层（Controller → Service）                            │
│ 认证 / 番茄钟状态机 / 任务与子任务 / 标签 / 周目标          │
│ 日统计 / 月报表 / 积分等级 / 成就 / 排行榜 / 精力打卡       │
│ AI 分析（DeepSeek + 轻量 RAG 检索）/ CSV 导出 / 海报       │
└──────────────┬────────────────────────────┬─────────────┘
               │                            │
┌──────────────▼─────────────┐  ┌───────────▼─────────────┐
│ MySQL 8（12 张表）          │  │ Redis（可选，自动降级）   │
│ 用户/任务/标签/番茄/统计/    │  │ refresh token 白名单      │
│ 目标/成就/打卡/积分等        │  └─────────────────────────┘
└────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────┐
│ 外部服务：DeepSeek API（失败/未配置时返回空，主流程不受影响）│
└─────────────────────────────────────────────────────────┘

定时任务：每天 0:30 重算昨日 daily_stat，修复聚合误差

可观测性链路：
Actuator（health / info / metrics） + Micrometer 业务埋点
  → /actuator/prometheus（文本格式） → Prometheus 抓取 → Grafana 面板
每条日志带 traceId（MDC），异步 SSE 任务手动传递，响应头回传 X-Trace-Id
```

**请求链路**：前端 `request()` 注入 `Authorization: Bearer <accessToken>` → `TraceIdFilter` 生成/沿用 traceId → `JwtInterceptor` 解析并校验用户 → `UserContext` 保存 `userId` → Service 层所有查询强制带 `userId` 隔离 → 统一 `Result` 包装返回。日志格式统一输出 `[traceId]`，前端报错时可凭响应头定位到完整请求链路。

---

## 四、核心功能列表

### 认证与账号

- 注册 / 登录 / 双令牌刷新（access 2h，refresh 7d）
- Redis 存 refresh token 白名单，支持吊销；Redis 故障自动降级为无状态校验
- 注销账号：密码二次确认，事务内物理删除全部数据
- 导出个人数据（JSON，不包含密码）

### 番茄钟

- 开始 / 完成 / 中断三态状态机，同一用户同时只允许一个进行中的番茄（数据库唯一约束兜底，并发点击也只成功一个）
- **服务端对账防作弊**：时长按服务端 `startTime` 计算，完成至少 30 秒、单次最多 4 小时
- 番茄可绑定任务，完成后任务 `actualPomodoros + 1`
- 刷新 / 切页后通过 `GET /api/pomodoro/page?status=RUNNING` 恢复进行中状态，计时以服务端时间为锚点
- 番茄历史分页查询

### 任务与目标

- 任务 CRUD、四象限优先级、截止日期、预估番茄数
- 子任务（父子结构），顶层列表可选过滤
- 标签：多对多绑定、按标签筛选
- 重复任务：每日 / 每周，任务打卡与连续打卡天数（streak）
- 周目标：按周一归一化，目标落库、达成数读时实时计算（避免计数漂移）

### 统计与激励

- 日统计：完成数、专注分钟、平均专注时长、效率得分（完成/中断时实时重算）
- 月报表：完成番茄、总时长、活跃天数、平均得分、完成率、最佳一天、逐日柱状图
- 热力图（近一年）、时段分布（近 30 天）
- 积分等级：完成番茄 +10、每日打卡 +5、解锁成就 +20，`level = points / 100 + 1`
- 成就徽章、全局排行榜（含我的排名）
- 精力自评打卡（单日 / 范围查询）
- CSV 导出（UTF-8 BOM，Excel 中文不乱码）

### AI 与分享

- AI 周报 / 日报 / 月报、效率问答、任务拆分、黄金专注时段
- **效率问答接入轻量 RAG**：BM25 检索近 90 天日报 / 任务 / 精力记录，按相关度 + 时间衰减取 Top 8，返回引用来源
- 周报 SSE 流式输出
- **可降级设计**：未配置 API Key 或调用超时，返回空结果并提示，不影响任何本地功能
- 分享海报：Java2D 服务端渲染 PNG（今日完成 / 专注分钟 / 效率得分 / 累计完成 / 连续打卡 / 等级积分）

### 工程化

- AOP 请求日志（耗时、用户、异常，不记录敏感参数）
- 登录接口限流 `@RateLimit`（10 次 / 60 秒）
- 打卡接口幂等 `@Idempotent`（5 秒窗口，防双击）
- 每日 0:30 定时重算昨日统计，兜底聚合误差
- 前端离线缓存：成功的 GET 响应按「用户 + 接口 + 参数」缓存 7 天，断网时回退最近一次数据并提示；写请求不缓存，避免伪造成功
- **写请求离线队列**：只排队可安全重放的接口（每日打卡 / 精力自评 / 周目标 / 任务状态），按用户隔离；恢复网络后顺序重放，页面先乐观更新并提示「已离线保存」，同步完成提示条数
- **跨端适配**：SSE 流式周报在 H5 用 `fetch` 读流，App / 微信小程序自动降级为普通周报接口；CSV 导出区分 H5 Blob 下载与 `downloadFile` 临时文件；同一套代码已通过 H5、App-Plus、微信小程序三端编译
- **可观测性**：Actuator 健康检查（DB 状态、磁盘、SSL）+ Prometheus 指标导出；自定义业务指标覆盖番茄启动/完成/中断、专注时长分布、AI 调用耗时与降级原因、限流与幂等拦截次数；每个请求生成 `traceId` 写入日志与响应头，支持沿用上游 traceId
- **可视化**：提供 Prometheus + Grafana 编排与预置面板（QPS、P95 延迟、5xx、JVM 堆、连接池、番茄事件、AI 降级、专注时长分位数）
- Docker Compose 一键部署（MySQL + Redis + App，可叠加可观测性套件）

---

## 五、✅ 项目难点 & 个人解决思路

> 这一节是面试讲述重点：每个问题都来自真实联调，记录「现象 → 根因 → 方案 → 验证」。

### 1. 番茄时长对不上：MySQL 小数秒四舍五入导致负时长 + 记录锁死

**现象**：开始后立刻中断，后端报「专注时长过短」（400），记录却停留在 `RUNNING`，之后再次开始被 409「已有进行中的番茄钟」永久挡住。

**根因**：`start_time` 列是 `DATETIME`（无小数位），Java 写入 `LocalDateTime.now()` 带纳秒，MySQL 会把 ≥ 0.5 秒的小数**四舍五入到下一秒**。随后按 `now()` 对账得到负秒数；异常发生在状态更新之前，记录没有落为 `INTERRUPTED`。

**方案**：
- 写入时统一 `.withNano(0)`，保证「写入值 = 读出值」；
- 对账时长用 `Math.max(0, ...)` 兜底，避免任何时钟/精度问题产生负数；
- 集成测试覆盖「回拨 start_time → 完成」链路。

**验证**：秒退中断返回 `INTERRUPTED`，之后可以正常再次开始；测试中断言时长 ≥ 阈值。

### 2. 服务端防作弊与前端计时漂移

**问题**：如果信任客户端上报的时长，改系统时间或伪造请求就能刷数据；而前端只靠 `setInterval` 计时，切后台、刷新页面都会不准。

**方案**：
- 后端只存 `startTime`，完成/中断时用服务器时间对账，并限制 `[30 秒, 4 小时]`；
- 前端计时以服务端返回的 `startTime` 为锚点，每秒用 `Date.now() - startTime` 重算剩余时间，而不是递减计数器；
- 页面 `onShow` 时查询 `status=RUNNING` 的记录恢复状态，避免刷新后丢失 `pomodoroId`。

**效果**：刷新、切页、切后台后计时连续；重复开始会被后端 409 拦截。

### 3. ThreadLocal 泄漏：拦截器异常时 `afterCompletion` 不执行

**现象**：token 过期场景下，登录接口日志里会带上**上一个请求**的 `userId`，存在越权风险。

**根因**：Spring MVC 在 `preHandle` 抛异常时，不会回调该拦截器的 `afterCompletion`（`interceptorIndex` 还没推进到它），`UserContext` 的 ThreadLocal 残留，被线程池复用的下一个请求读到。

**方案**：新增最高优先级的 `UserContextFilter`，在 `doFilter` 前后用 `try/finally` 强制 `clear()`，覆盖拦截器白名单内的 `/api/auth/**` 请求。

**验证**：60 轮并发/复用线程请求后 ThreadLocal 无残留。

### 4. 任务标签的 N+1 查询

**问题**：任务列表需要展示标签，最初逐条查关联表和标签表，一页 50 条就是 100+ 次查询。

**方案**：`fillTags()` 先收集本页所有 `taskId`，用 `IN` 一次查 `task_tag`，再按 `tagId` 一次查 `tag`，内存分组回填。**固定 2 条查询**，与页大小无关；同时过滤已删除标签产生的脏关联。

**效果**：任务列表与子任务列表共用同一逻辑，不随数据量线性增加查询次数。

### 5. @TableLogic 软删与「注销账号」的冲突

**问题**：任务表使用 MyBatis-Plus 逻辑删除，`delete` 只会把 `deleted` 置 1。注销账号要求彻底删除数据，软删会留下用户痕迹。

**方案**：在 Mapper 中写 `physicallyDeleteByUserId` 原生 SQL 绕过 `@TableLogic`；先删 `task_tag` 关联（含已软删任务的关联），再删任务，其余表按 `userId` 物理删除，整个流程放在一个事务里。

**验证**：注销后重新注册同名账号，查不到任何旧任务/番茄/积分数据；旧 access token 因用户不存在立即失效。

### 6. 旧 token 在账号注销后仍然有效

**问题**：JWT 是无状态的，注销账号后 token 在有效期内仍能通过签名校验。

**方案**：`JwtInterceptor` 在解析 token 后增加 `userMapper.selectById(userId)` 判空，用户不存在直接 401。

**效果**：注销立即生效，无需维护 access token 黑名单。

### 7. 前端并发 401 导致重复刷新 token

**问题**：页面同时发起多个请求，access token 过期时每个请求都会触发一次 refresh，造成刷新风暴，且可能互相覆盖 token。

**方案**：`utils/request.js` 用模块级 `refreshing` Promise 做**单飞（single-flight）**：第一个 401 触发刷新，其余请求复用同一个 Promise；刷新成功后各自重放一次原请求，失败则统一清空登录态回登录页。同时排除 `/api/auth/**` 自身的 401（密码错误不能触发刷新）。

### 8. 外部 AI 依赖不能拖垮主流程

**问题**：DeepSeek 未配置 Key、网络超时或返回异常时，日报/周报接口如果直接抛错，会连带影响统计页面。

**方案**：
- `DeepSeekClient` 统一捕获异常，返回 `null`；
- Service 层把 AI 文本作为可选字段返回，前端展示「AI 未配置或暂无数据」；
- SSE 流式接口在异常时通过 `SseEmitter` 发送降级提示并正常结束；
- 连接/读取超时分别配置（10s / 120s），避免线程长期占用。

**效果**：不配置任何 Key、不装 Redis，项目依然可以完整运行和演示。

### 9. 统计数据的实时性与一致性

**问题**：日统计如果只在完成番茄时 `+1`，一旦请求失败或重复计算就会漂移；周目标如果存「已达成数」，也会与番茄记录不一致。

**方案**：
- `daily_stat` 不做增量累加，完成/中断时按当天记录**全量重算**（幂等），并加每日 0:30 的定时任务兜底；
- 周目标只存目标值，`achievedPomodoros` 和百分比在读取时从 `pomodoro_record` 实时统计。

**取舍**：牺牲一点查询性能，换取数据永远可自愈；当前数据量下完全够用。

### 10. 集成测试在 JDK 25 上跑不起来

**问题**：Spring Boot 测试默认使用 Mockito inline mock maker，JDK 25 下自附加 agent 失败（`Could not self-attach to current VM`）。

**方案**：项目不需要 mock 静态方法/final 类，改用子类 mock maker —— 新增 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`，内容为 `mock-maker-subclass`，无需任何 JVM 参数。

**测试覆盖**：全量 **42 个测试**（6 个集成测试类共 28 个用例 + 3 个单测类共 14 个用例），集成用例覆盖：
- 认证、任务/标签/子任务/打卡；
- 番茄主链路（完成 → 任务计数 → 周目标 → 日统计 → 积分 → 月报 → CSV）；
- 进行中恢复与跨用户隔离；
- **AI 全部接口在未配置 Key 时的降级返回**（结构化数据正常、AI 文本为 null）；
- **登录限流**（同 IP 第 11 次返回 429）与**每日打卡幂等**（5 秒内重复提交被切面拦截、不同用户互不影响）；
- **并发场景**：8 线程同时开始番茄只成功 1 个、8 线程同时打卡只产生 1 条记录和 1 次积分；
- **可观测性**：Actuator 健康/信息/Prometheus 端点、traceId 生成与上游沿用/非法值回退、番茄启停指标自增、AI 降级/限流/幂等计数；
- **SSE 流式**：降级路径只发一帧且立即关闭、帧中文的 **UTF-8 原始字节**、traceId 透传、无 token/坏 token/refresh token 当 access token 一律业务 401、非法 `weekStart` 400、两条并发流互不阻塞、按前端真实 `Accept: text/event-stream` 请求不得 406；
- **统计口径与日期边界**：跨天/跨月/跨周归属（含月末 23:59、周一 00:00 边界）、RUNNING 不进任何统计、INTERRUPTED 只进日统计与报表、不进热力图/时段分布、闰年与世纪年天数、366 天跨度上限、空数据必须补 0 而不是缺字段；
- **写入侧对账边界**：不足 30 秒不能完成但记录必须留在 RUNNING（客户端还能中断）、超过 4 小时不能完成但**必须**能中断（否则用户被永久锁死）。

另有 6 个 RAG 检索单测（中文切词、关键词排序、时间衰减、无命中回退、字符预算、空集合）、4 个效率得分公式单测（三因子与封顶）、4 个 JWT 单测。多数集成用例使用 `@Transactional` 自动回滚；SSE 与并发用例需要真实 HTTP / 事务真实提交，因此不开启测试事务，改为独立用户 + 用例结束调用注销接口物理清理。限流/幂等/业务指标的内存计数不随事务回滚，测试用「独立 IP / 独立用户」隔离、指标用前后差值断言，避免用例间互相污染。

### 11. 测试数据的时间回拨陷阱

**现象**：集成测试中把 `start_time` 回拨 60 秒后完成番茄，断言时长 ≥ 60 却偶发失败。

**根因**：`DATETIME(0)` 会对小数秒四舍五入，回拨 60 秒实际可能只落了 59 秒。

**方案**：回拨 120 秒并 `.withNano(0)`，给精度留余量；这也反向验证了第 1 个问题的修复思路。

### 12. 并发开始番茄：先查后插的竞态

**现象**：并发测试用 8 个线程同时调用 `/api/pomodoro/start`，结果 **8 个请求全部成功**，数据库出现 8 条 `RUNNING` 记录，之后完成/中断逻辑全部错乱。

**根因**：`start()` 的逻辑是「`selectCount` 查进行中记录 → 没有则 `insert`」。查询和插入之间没有原子性，8 个请求同时通过检查后再各自插入，典型的 check-then-act 竞态。进程内加锁只能解决单实例，且会阻塞无关用户。

**方案**：把约束下沉到数据库。
- 在 `pomodoro_record` 加**生成列** `running_user_id`：`IF(status = 'RUNNING', user_id, NULL)`；
- 在生成列上建唯一索引。MySQL 唯一索引允许多个 `NULL`，因此约束等价于「每个用户最多一条进行中记录」；番茄完成/中断后 `status` 改变，生成列自动变 `NULL`，唯一约束自动释放；
- `start()` 捕获 `DuplicateKeyException`，统一转换为业务 409「已有进行中的番茄钟」。

**效果**：并发测试由 8/8 成功变为 **1 成功 + 7 个 409**；约束在数据库层生效，多实例部署同样有效，不需要引入分布式锁。

**取舍**：生成列是 MySQL 特性，换库需要等价方案（部分唯一索引 / 事务锁）；项目已明确 MySQL 8，因此可以接受。

### 13. AI 问答的上下文：全量塞太贵，关键词检索又不懂中文

**问题**：最初 `/api/ai/ask` 只拼接近 7 天的汇总数据。用户问「论文任务最近进展如何」时，模型看不到 7 天前的任务记录；但把 90 天所有明细直接塞进提示词，上下文长度和调用成本都不可控。

**方案**：在应用内实现轻量 RAG，不引入向量库和 Embedding 服务。
- **文档构造**：把日报、任务聚合（完成数 / 累计分钟 / 最近完成日）、精力自评构造成带日期的文档，只存结论型数据，避免文档数量爆炸；
- **中文切词**：用「单字 + 相邻双字 bigram」切分（`ChineseTokenizer`），既能命中「论文」「专注」等词，又不用引入分词库；
- **相关度排序**：BM25 打分抑制高频词，再乘时间衰减因子（当天 1.4 倍 → 90 天 1.0 倍），近期记录优先但老数据仍可凭关键词胜出；
- **预算控制**：Top 8 + 1800 字符上限，提示词 = 近 7 天概览 + 检索到的相关历史；全部不相关时回退最近记录，避免空上下文；
- **可解释性**：返回 `sources` 引用（如「日报 2026-10-18」），前端展示回答依据。

**效果**：问「论文进度」能准确召回论文任务的累计番茄与最近完成日，不相关的健身记录不会混入；未配置 DeepSeek Key 时检索和引用照常返回，仅 AI 文本降级为 null。

**取舍**：BM25 不理解同义词（「专注」查不到「沉浸」），后续可替换为向量检索；但当前方案零额外服务、零推理成本，适合本项目的部署场景。

### 14. 可观测性落地：可选依赖的健康误报 + 异步线程 traceId 丢失

**现象**：
1. 接入 Actuator 后，本机没装 Redis，`/actuator/health` 整体返回 `DOWN`，但应用其实完全可以正常工作（refresh token 已降级）；
2. SSE 流式周报的日志里没有 traceId，无法和触发它的 HTTP 请求串起来。

**根因**：
1. Spring Boot 自动配置的 `RedisHealthIndicator` 把 Redis 当作强依赖，连接失败会把聚合健康状态拖成 `DOWN`；
2. `TraceIdFilter` 在 `finally` 中清理 MDC，而流式周报把实际调用丢给线程池异步执行，线程池线程不会继承父线程的 MDC。

**方案**：
- 关闭 Redis 健康检查（`management.health.redis.enabled=false`），与「Redis 可选、可降级」的架构约定保持一致；MySQL 作为强依赖仍参与健康判定；
- `TraceIdFilter` 用白名单正则校验入站 `X-Trace-Id`（防日志注入），生成/沿用后写入 MDC 与响应头，`finally` 清理避免 Tomcat 线程复用串号；
- 异步任务提交前 `MDC.getCopyOfContextMap()` 拷贝上下文，任务内 `setContextMap`，`finally` 中 `clear()`，避免污染复用线程；
- 业务指标只使用低基数标签（feature / outcome / reason / method），不把 userId、任务标题等高基数值放进标签，防止 Prometheus 时间序列爆炸。

**验证**：健康检查只报告 `db / diskSpace / ping / ssl` 且整体 `UP`；登录响应头返回 16 位 traceId，同一请求的所有日志（WebLogAspect、异常、SQL）前缀一致，传非法 traceId 会被拒绝并回退为服务端生成；对番茄启动/中断、AI 降级、限流、幂等分别断言指标自增。

### 15. 写请求的离线队列：能重放什么、不能重放什么

**现象**：弱网或断网时，打卡、精力自评、周目标等写操作直接报「网络异常」，用户的操作白做了；但也不能简单地把所有失败请求都排队重放。

**根因与风险**：
1. 非幂等写（如任务创建）重放会重复创建；任务删除重放可能误删后来重建的同名数据；
2. 番茄的开始/完成不能延迟重放：服务端按 `startTime` 对账，离线期间的时间会被算进专注时长，直接破坏防作弊语义；
3. 切换账号后，A 的待同步操作不能以 B 的身份重放；同一操作连点还会在本地堆出多条；
4. 无限重试会死循环，本地存储也会无限增长。

**方案**：
- 只排队「按天 upsert / 状态更新」这类可安全重放的接口（打卡、精力、周目标、任务状态与任务打卡），创建/删除/番茄生命周期不排队；
- 队列 key 带 `userId`；相同 `method + url + body` 10 秒内去重；上限 50 条、3 天过期；网络连续失败 5 次放弃；
- 恢复网络后顺序重放：`409 / 400 / 404` 视为已生效或不可重放，直接丢弃避免死循环；`401` 保留队列等用户重新登录；网络错误、`429` 与服务端 `5xx` 视为临时故障，保留操作并累加失败次数，下一轮再试；
- 页面拿到 `{ queued: true }` 先做乐观更新并提示「已离线保存」；任意请求成功或网络恢复（`onNetworkStatusChange`）触发同步，重连同步给出「已同步 N 条」提示，启动/前台恢复则静默同步。

**验证**：Node 模拟 uni storage/request，15 个用例覆盖去重、用户隔离、重放清空、`409` 丢弃、断网保留与 5 次放弃、`5xx` 保留重试、业务码透传、队列上限与清空；无头 Edge 实测断网保存 → 本地队列 1 条 + 「已离线保存」提示，恢复网络 → 自动重放、队列清空、服务端读到 `energyLevel=4` + 「已同步 1 条」提示。过程中还发现 uni-app H5 的 `onNetworkStatusChange` 监听的是 `navigator.connection` 的 `change` 事件（而非 `window` 的 `online`），验证脚本据此模拟真实网络切换。

### 16. 挂机过夜的番茄把用户锁死：中断接口复用了「完成」的上限校验

**现象**：一条番茄挂了一夜（超过 4 小时），第二天点「完成」报 400「专注时长异常」，点「中断」**同样**报 400，之后任何「开始」都被 409「已有进行中的番茄钟」挡住——不是单个按钮失灵，而是整个番茄功能对这个人不可用。

**根因**：两条链路叠加。① 防作弊的时长上下限（≥30 秒、≤4 小时）本质是「完成」的准入条件，但 `interrupt()` 复用了同一个对账校验，于是超时记录在**两条终态路径上都被拒绝**；② 这个项目用「每用户最多一条 RUNNING」的唯一索引（生成列 `running_user_id = IF(status='RUNNING', user_id, NULL)` + `uk_pr_running_user`）来根除并发开始番茄的竞态，而这把锁**只能靠完成或中断释放**。两条路都被堵死，锁就永久留在数据库里。

**方案**：把校验的归属分清楚——上限是准入条件，只属于「完成」；「中断」是释放锁的唯一出路，永远不能因为时长异常而失败。`interrupt()` 改为只按实际秒数对账、并把落库时长封顶到 4 小时：既保证一定能释放锁，也不让异常长的记录污染日统计的分钟数。

**验证**：`StatBoundaryIntegrationTest#reconcileBoundsNeverLockTheUserOut` 用 mapper 把 `start_time` 回拨 5 小时，实测「完成」返回 400、`RUNNING` 状态不被破坏；「中断」返回 `INTERRUPTED`、时长恰好封顶 4×3600、日统计单日最多贡献 240 分钟；随后 `start` 立即恢复可用。顺带发现前端在同一场景下点「完成」是静默失败（`catch` 只吞掉错误），用户的正确出路是点「中断」——这类「后端必然拒绝」的路径前端应当给出引导，而不是让用户自己猜。

### 17. SSE 中文乱码：`produces` 修不了它，还可能把前端打成 406

**现象**：流式周报 `/api/ai/weekly-report/stream` 在浏览器里中文一切正常，换 Java/OkHttp 客户端或 App 端请求库读同一个接口却是乱码。

**根因**：分两层，且第一层结论很容易被误判。① 服务端写出的事件帧**字节本身是 UTF-8 正确的**（拿原始字节按 UTF-8 解能还原中文），乱码发生在客户端解码——响应头是裸的 `text/event-stream`，不声明 charset，Spring 的 `StringHttpMessageConverter` 缺省按 ISO-8859-1 解。浏览器 `EventSource` 和前端 `fetch + TextDecoder('utf-8')` 按规范强制 UTF-8，所以 H5 一直看不出问题，只在跨端暴露。② 想当然的修法 `produces = "text/event-stream;charset=UTF-8"` 有两个坑：`produces` 参与内容协商，而前端发的恰恰是不带参数的 `Accept: text/event-stream`；而且 SSE 的 Content-Type 是 `SseEmitter`（继承 `ResponseBodyEmitter`）自己在 `extendResponse()` 里写死的，`produces` 根本覆盖不了它。

**方案**：子类化 `SseEmitter` 覆写 `extendResponse()`，在 `super` 之后把 Content-Type 设为带 UTF-8 的 `text/event-stream`（`Utf8SseEmitter`），只改响应头、完全不碰内容协商；`AiController` 的 `produces` 保持不带参数。

**验证**：新建 `SseStreamIntegrationTest`，用 `RANDOM_PORT` 起真实 Tomcat（SSE 的写与 complete 发生在业务线程池线程上，MockMvc 的 mock response 反映不了「帧已刷出、连接已关闭」），且一律断言**原始字节**而不是 `String`——否则客户端解码会把结论带偏。另外专门加了一个「按前端真实 `Accept: text/event-stream` 发请求必须 200 而不是 406」的回归用例，防止以后有人为了修中文再把 charset 加回 `produces`。初版确实踩了这个坑：改动只有一行 `produces`，而跨端出问题的解法反而要靠覆写 `extendResponse`。

### 18. 测试按「当前时刻」断言日期：跨过午夜就必红

**现象**：全量测试在白天跑全绿，某次恰好跨过午夜执行，`CoreFlowIntegrationTest` 的日统计断言突然变红（断言完成 1 个，实际 0 个）。

**根因**：该用例为了绕过「完成需 ≥30 秒」的真实等待，会把 `start_time` 回拨 120 秒，但后续断言仍用 `LocalDate.now()` 查日统计。回拨跨过午夜时，记录已经归属**昨天**（跨月 1 号 00:00、跨周周一 00:00 还会连带换月换周），断言与实际归属自然对不上——这不是被测逻辑的问题，而是测试自身依赖了运行时刻。

**方案**：回拨时把实际使用的 `LocalDateTime startAt` 提成变量，后续所有日期断言统一用 `startAt.toLocalDate()`（日统计、月报表、CSV 区间），周目标显式用该日期所在的周查询，让整个用例与运行时刻解耦。

**验证**：修完在跨午夜窗口复跑全量通过；同时这也暴露了另一类测试基础设施坑——**限流计数在内存中且按 Spring context 共享**，配置完全相同的测试类会复用同一个 context，各 `setUp` 的登录次数在 60 秒窗口内累加，第 11 次起整批用例在 `setUp` 就 429 全红，因此每个用例登录都改用独立 IP 隔离。

---

## 六、本地环境搭建、运行教程

### 环境要求

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 | 后端运行/编译 |
| Maven | 3.9+ | 也可用 IDEA 内置 Maven |
| MySQL | 8.0 | 需要提前启动 |
| Redis | 7（可选） | 不装会自动降级 |
| HBuilderX | 最新版 | 运行 uni-app 前端 |
| Docker | 可选 | 一键部署整套环境 |

### 1. 初始化数据库

```bash
mysql -u root -p < pomodoro-server/sql/schema.sql
```

脚本会创建 `pomodoro` 库和 12 张表。

> 如果使用本次更新前就已存在的数据库，需额外执行 `sql/schema.sql` 底部的 ALTER 语句，为 `pomodoro_record` 增加 `running_user_id` 生成列与唯一索引（并发开始番茄的兜底约束）。

### 2. 启动后端

```bash
cd pomodoro-server
export JAVA_HOME=/path/to/jdk-25          # Windows 示例：D:\develop
export MYSQL_PASSWORD=123456              # 改成你的 MySQL 密码
# 可选：export REDIS_HOST=... DEEPSEEK_API_KEY=...
mvn spring-boot:run
```

启动成功：`http://localhost:8080`。

- 未配置 `DEEPSEEK_API_KEY`：AI 功能自动降级，其余功能正常。
- 未启动 Redis：refresh token 改为无状态校验，登录不受影响。

### 3. 启动前端（H5）

1. 用 HBuilderX 打开项目根目录；
2. 菜单「运行 → 运行到浏览器 → Edge / Chrome」，默认端口 `5173`；
3. 确认 `utils/request.js` 中的 `BASE_URL` 指向后端地址（默认 `http://localhost:8080`）；
4. 注册账号后即可体验全部功能。

### 4. Docker 一键部署（可选）

```bash
cd pomodoro-server
docker compose up -d --build
```

包含 MySQL（自动执行 `sql/schema.sql`）、Redis、应用三个服务。停止：`docker compose down`；清空数据重建：`docker compose down -v`。

服务器部署可直接使用 CI 构建并推送到 GHCR 的镜像，无需在服务器上编译：

```bash
cd pomodoro-server
echo $CR_PAT | docker login ghcr.io -u <GitHub 用户名> --password-stdin
IMAGE_TAG=latest docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
```

> GHCR 包首次推送后默认私有：可在 GitHub 仓库 → Packages → 包设置中改为 Public，或保持私有并在服务器上先 `docker login`。

### 5. 指标与看板（可选）

只启动后端即可访问基础端点：

```bash
curl http://localhost:8080/actuator/health     # 健康检查（db / diskSpace / ssl）
curl http://localhost:8080/actuator/info       # 应用名与版本
curl http://localhost:8080/actuator/prometheus # Prometheus 文本格式指标
```

需要看板时，叠加可观测性编排：

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

- Prometheus：`http://localhost:9090`（抓取 `app:8080/actuator/prometheus`）
- Grafana：`http://localhost:3000`（默认 `admin / admin`，可用 `GRAFANA_PASSWORD` 覆盖），预置「Pomodoro Server - Overview」面板

核心指标：

| 指标 | 说明 |
| --- | --- |
| `http_server_requests_seconds_*` | QPS、P95 延迟、状态码分布（Spring 自动埋点） |
| `pomodoro_started/completed/interrupted_total` | 番茄生命周期事件，启动指标带 `bound_task` 标签 |
| `pomodoro_focus_duration_seconds_*` | 完成番茄的专注时长分位数 |
| `ai_call_duration_seconds_*` / `ai_degraded_total` | AI 调用耗时与降级原因（feature / reason） |
| `rate_limit_triggered_total` / `idempotent_rejected_total` | 限流与幂等拦截次数 |
| `jvm_*` / `hikaricp_*` | JVM 内存/GC、数据库连接池 |

> 安全提示：Actuator 端点不走 JWT 拦截器。生产环境建议只暴露 `health,prometheus`，并通过网关或内网访问，避免 `metrics`、`env` 等端点匿名可达。

### 6. 运行测试

```bash
cd pomodoro-server
mvn test
```

测试需要本地 MySQL 可连接；集成测试使用事务回滚，不会污染数据库。可观测性测试断言指标自增，由于指标注册表不随事务回滚，统一使用「执行前后差值」断言。

### 7. 持续集成（GitHub Actions）

工作流文件：`.github/workflows/ci.yml`，push 到 `main` / `master` 或提交 PR 时自动执行：

1. 启动 MySQL 8、Redis 7 服务容器；
2. 用 `sql/schema.sql` 初始化数据库；
3. JDK 25 + Maven 执行全部测试（真实 MySQL + Redis，非 mock）；
4. 打包 `pomodoro-server.jar` 并上传产物；
5. 用 Dockerfile 构建后端镜像；push 到主分支时推送到 GHCR（`ghcr.io/xia-cloud1/pomodoro-ai-assistant`），PR 只验证构建、不推送。

前端使用 HBuilderX 构建，暂无 Node 构建链，因此暂未纳入 CI。

### 项目目录

```
番茄钟分析助手与ai效率分析系统/
├── api/                 # 前端接口封装（按模块拆分）
├── pages/               # 专注 / 任务 / 统计 / 我的 / AI / 排行榜 / 海报 / 成就
├── utils/               # request 封装、离线缓存 / 写队列、i18n、主题
├── .github/workflows/   # GitHub Actions CI（测试 + 镜像构建）
├── pomodoro-server/
│   ├── src/main/java/com/pomodoro/
│   │   ├── controller/  # REST 接口
│   │   ├── service/     # 业务逻辑
│   │   ├── mapper/      # MyBatis-Plus Mapper
│   │   ├── entity/      # 数据库实体
│   │   ├── security/    # JWT、拦截器、ThreadLocal
│   │   ├── common/      # 统一返回、异常、AOP 切面、UTF-8 SSE emitter
│   │   ├── observability/ # traceId 过滤器 + 业务指标埋点
│   │   └── ai/          # DeepSeek 客户端
│   ├── src/test/        # JWT/检索/效率得分单测 + 核心链路、统计边界、SSE、并发、可观测性集成测试
│   ├── sql/schema.sql   # 建库建表脚本
│   ├── observability/   # Prometheus 抓取配置 + Grafana 预置面板
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── docker-compose.observability.yml
└── README.md
```

---

## 七、项目亮点

1. **完整的业务闭环**：专注 → 任务归因 → 统计反馈 → AI 洞察 → 激励（积分/成就/排行），不是单点功能 Demo。
2. **服务端可信计时**：不信任客户端时长，服务端对账 + 上下限校验；前端以服务端时间为锚点，刷新不丢状态。
3. **可降级架构**：Redis、DeepSeek 都是可选依赖，缺任何一个项目都能完整启动和演示，适合本地/面试环境。
4. **真实踩坑与修复**：四舍五入导致的负时长、ThreadLocal 泄漏、软删与注销冲突、N+1、并发 401 等问题均有明确定位、修复和回归验证。
5. **有回归保障**：42 个后端测试，其中 28 个是真实 Spring 上下文 + MySQL 的集成测试，覆盖认证、任务、番茄、统计与日期边界、SSE 流式、导出、数据隔离、AI 降级、限流、幂等、并发竞态与可观测性指标；另有 RAG 检索、效率得分公式、JWT 共 14 个单测。前端离线队列用 Node 模拟 uni storage/request 做了 15 个用例，并用无头 Edge 走通「断网保存 → 联网自动同步」。
6. **工程化完整**：统一返回体、全局异常处理、AOP 日志、限流、幂等、定时补偿、Docker Compose 一键部署；GitHub Actions 自动跑测试、构建镜像并发布到 GHCR。
7. **前端体验**：中英双语（响应式 i18n）、暗黑模式、H5 CSV 直接下载、SSE 流式 AI 输出；GET 接口离线缓存，断网时页面仍可展示最近数据；可安全重放的写操作进入离线队列，联网自动补交，刷新后可恢复进行中的番茄状态。
8. **可控成本的 AI 问答**：BM25 + 中文 bigram + 时间衰减做历史数据检索，Top 8 / 1800 字符预算控制提示词，无命中自动回退；回答附带引用来源，可解释、可验证。
9. **数据隔离**：所有查询强制带 `userId`，跨用户访问返回 404；集成测试专门覆盖越权场景。
10. **可观测性闭环**：健康检查 + JVM/HTTP 自动指标 + 业务指标埋点 + traceId 全链路日志 + Prometheus/Grafana 预置面板；指标标签控制基数，测试覆盖指标自增与 traceId 边界。

---

## 八、后续优化方向

1. **写接口幂等扩展**：目前只有每日打卡使用 `@Idempotent`，后续可扩展到任务创建、番茄完成等写接口，避免弱网重试产生重复数据。
2. **refresh token 轮换**：每次刷新签发新 token 并吊销旧 jti，进一步降低 token 泄露风险。
3. **统计数据预聚合**：数据量增大后，热力图/月报改为按日预聚合或加覆盖索引，避免范围扫描。
4. **AI 检索升级**：当前 BM25 已覆盖关键词召回，后续可接入 Embedding / 向量库补足同义词与口语化提问；周报支持异步任务化，避免长连接占用。
5. **告警与链路追踪**：基于现有指标配置 Prometheus 告警（5xx、AI 降级率、连接池等待），并接入 OpenTelemetry 将 traceId 上报到 Jaeger/Tempo，补足跨服务链路视图与慢 SQL 定位。
6. **自动化部署**：在 GHCR 镜像发布的基础上，通过 SSH 自动拉取镜像并重启服务（需要配置服务器地址、SSH 密钥等仓库 Secrets）。
7. **前端工程化**：页面组件拆分、App / 小程序真机联调；离线队列可进一步引入后台同步与冲突提示。
8. **测试完善**：SSE 流式、统计与日期边界、并发竞态已补齐（42 个用例）；后续可补前端组件级测试，并把前端产物构建纳入 CI（当前 uni-app 由 HBuilderX 构建，暂无 Node 构建链）。

---

> 维护约定：每完成一个模块，同步更新本文档对应章节；核心业务逻辑在代码中保留中文注释，说明「为什么这么做」而非仅描述代码行为。
