# AFS-CMS 项目事实卡（v2·全部实测）

> **本文件是唯一数据来源。** 简历、STAR、问答、八股里出现的每个数字，都必须能在这里找到出处。
>
> 与 v1 的区别：v1 的数字多为**静态数文件**，本版全部改为**运行态实测**——
> 跑通了 `mvn clean test`（31 个用例全绿），启动了服务探活，并直接查了本地 H2 库。
>
> 核验时间：**2026-11-25 14:00–14:10**（Asia/Shanghai）
> 核验方式：① `mvn clean test` 全量单测 ② `java -jar` 起服务 + 签名调 API ③ H2 直连查库

---

## 一、项目一句话

一个 **JDK 17 + Spring Boot 2.7 + LangChain4j 1.0.1 + LangGraph4j 1.5.14 + React 18** 的海外广告投放管理后台（CMS），
在同一进程内集成基于编译态 `StateGraph` 的多智能体投放优化平台：
8 节点优化图、2 节点 Copilot 图、8 个只读取数工具、JDBC 检查点、人工审批、三级安全策略、SSE 节点级遥测。

- 零外部凭据可跑：本地 H2 文件库 + 合成工作区
- 生产形态可换：MySQL + 真实渠道凭据（AES-GCM 落库）
- **刻意不用 Redis**：互斥是 DB 行锁表（`distributed_lock` 表已确认存在），缓存是 60 秒 TTL 进程内字段

---

## 二、可写进简历的数字（实测口径）

### 2.1 代码规模

| 维度 | 实测值 | 核验方式 |
| --- | --- | --- |
| **主源码 Java 文件** | **245** | `mvn` 编译输出「Compiling 245 source files」 |
| 测试 Java 文件 | 12 | 编译输出「Compiling 12 source files」 |
| 合计 `src/**/*.java` | 257 | ⚠️ **257 = 245 主 + 12 测**，简历只能写 245 |
| Controller 类 | **52** | `*Controller.java` 计数 |
| MyBatis Mapper | **42**（`dao` 32 + `publicapi` 10） | `*Mapper.java` 计数；无 XML，全注解式 |
| HTTP 映射注解 | 286 | `@*Mapping` 匹配（含类级，简历别单列） |
| 包分布 | agent 54 / dao 65 / publicapi 48 / controller 41 / service 17 / utils 10 / task 6 / config 3 | 各包计数 |

### 2.2 数据规模（直连 H2 查询）

| 维度 | 实测值 | 核实语句 |
| --- | --- | --- |
| **库表总数** | **63** | `information_schema.tables WHERE table_schema='public'` |
| **站点数** | **32** | `SiteController.sites()` 32 条；API 返回 distinct site = 32 |
| **活动数** | **640** | `SELECT COUNT(*) FROM ad_resource` → **640** |
| 活动状态分布 | PASS **256** / ARCHIVE **320** / AUDITING **32** / REJECT **32** | `GROUP BY state` |
| 报表数据 | **27,124 行**，覆盖 **1,341 个自然日**（2023-03-26 → 2026-11-25） | `COUNT(*)`、`COUNT(DISTINCT date)` |
| 文章 | **842** | `COUNT(*) FROM article_info` |
| 自动化规则 | **12** | `COUNT(*) FROM ad_resource_rule` |
| 报表渠道配置 | **672** | `COUNT(*) FROM report_data_config` |
| 用户 | 15 | `COUNT(*) FROM user` |

### 2.3 运行态证据（服务实跑）

| 维度 | 实测值 | 核实方式 |
| --- | --- | --- |
| **单元/集成测试** | **10 个类 / 31 个用例 / 0 失败 0 错误 0 跳过** | `mvn clean test` → BUILD SUCCESS |
| 打包产物 | `target/afs-cms.jar` ≈ **194 MB**（fat jar，前端内嵌） | `mvn package` |
| 冷启动耗时 | **8.54 秒**（local-cms，9901） | 启动日志 `Started Application in 8.54 seconds` |
| 智能体运行记录 | **78 次优化运行**（25 待审批） | `optimization_run` |
| 节点执行记录 | **461 次节点执行**，30 个 run、9 个会话 | `agent_execution_trace` |
| Token 消耗 | 输入 **832,214** / 输出 **438,434** / 合计 **1,270,648** | 遥测接口 `totals` |
| 工具调用 | **395 次** | 遥测接口 `totals.totalToolCalls` |
| 节点平均耗时 | **8,647 ms**（含模型调用） | 遥测接口 `avgNodeDurationMs` |

> 这四个运行态数字是**这个项目最硬的证据**：它证明系统不是「能启动的 Demo」，
> 而是真的被跑了 78 次、461 个节点、127 万 token。面试时可当场打开控制台「系统运维」页复核。

### 2.4 测试明细（可背下来，被问就说）

| 测试类 | 用例 | 耗时 | 覆盖点 |
| --- | --- | --- | --- |
| `AdOpsInsightsTest` | 3 | 6.24s | 洞察聚合与异常检测 |
| `AgentGuardrailsAndReplayTest` | 1 | 1.19s | 护栏边界、检查点重放 |
| `LangGraphCommercialAgentTest` | 1 | 4.34s | 优化图端到端：状态→wiki图→活动→扫描→建单→审批→遥测 |
| `LocalConsoleIntegrationTest` | 2 | 1.74s | 本地控制台链路（MockMvc） |
| `OverseasOperationsTest` | 11 | 0.33s | 海外运营：同期群、汇率、合规、规则 |
| `PlatformEconomicsTest` | 1 | 0.10s | 平台经济学基线 |
| `SimulationWorkflowTest` | 4 | 0.95s | 五 Agent 闭环与模拟工作区 |
| `StaffWorkflowTest` | 1 | 1.39s | operator / reviewer 双人审批 |
| `ConsoleAuthorizationTest` | 3 | 0.02s | 控制台路径级鉴权 |
| `PlatformCampaignCredentialTest` | 4 | 0.19s | 渠道凭据加密与校验 |
| **合计** | **31** | | **全部通过** |

---

## 三、技术栈（出自 `pom.xml`，非推测）

| 层 | 选型 |
| --- | --- |
| 语言 | Java 17（compiler `release 17`） |
| 框架 | Spring Boot 2.7.0、Spring Security、Spring Data JDBC、WebSocket、Validation |
| 持久层 | MyBatis Starter 2.2.2、MySQL Connector、H2 2.1.212（`MODE=MySQL;DATABASE_TO_LOWER=TRUE`） |
| AI 编排 | LangChain4j 1.0.1（core + open-ai）、LangGraph4j 1.5.14（core + langchain4j） |
| 渠道 SDK | Google Ads 38.0.0、AdSense v2、阿里云 OSS 3.15.1、AWS S3 1.12.30 |
| 抓取/解析 | Jsoup 1.14.3、HtmlUnit 2.70.0、webp-imageio |
| 前端 | React 18、TypeScript 5.6、Vite 5.4（产物内嵌 JAR 的 `static/`） |
| 前端规模 | 19 个业务模块（`frontend/src/modules/*.ts`）、4 组 13 个导航入口 |

---

## 四、智能体：接口实测返回（可直接背）

调用 `GET /cms/agent/status`（签名鉴权后）实测返回：

```
engine         : LangGraph4j + LangChain4j
langGraphVersion: 1.5.14
langChainVersion: 1.0.1
timeoutSeconds : 90
configured     : true
tools          : dailyReport, countryBreakdown, creativeAndLandingContext, ruleContext,
                 peerBenchmarks, historicalRuns, scenarioCatalog, simulateBudget   ← 恰好 8 个
graphs.optimization: Mermaid 拓扑，节点恰为 8 个
```

Mermaid 实测节点：`intake_context`、`rule_baseline`、`research_specialist`、`strategy_planner`、
`tool_executor`、`guardrail_critic`、`self_reflect`、`finalize_plan`。

### 4.1 图拓扑（`AdOptimizationGraph:150-206`）

```
START → intake_context
  ├─ 未配置模型 / RULES_ONLY → rule_baseline → guardrail_critic → finalize_plan → END
  ├─ DEEP   → research_specialist ⇄ tool_executor → strategy_planner ⇄ tool_executor
  └─ STANDARD → strategy_planner ⇄ tool_executor
                strategy_planner → guardrail_critic
guardrail_critic ─ 违规 → self_reflect → strategy_planner（回环，最多 1 次）
                 └ 通过 → finalize_plan → END
```

关键常量：`MAX_TOOL_ROUNDS=5`、`MAX_REVISIONS=1`、`MAX_TOOL_OUTPUT=6500`、`setMaxIterations(24)`。

### 4.2 护栏四类硬校验（`AdOptimizationGraph:474-493`）

| 校验 | 规则 |
| --- | --- |
| 枚举 | `action ∈ {HOLD, REDUCE, INCREASE}` |
| 幅度 | `abs(changePercent) ≤ effectiveLimit` |
| 符号 | INCREASE 必须正、REDUCE 必须负、HOLD 必须 0 |
| 结构 | 单个合法 JSON，Markdown 围栏自动剥离 |

`effectiveLimit() = max(0, min(input.maxChangePercent, context.budgetChangeCap))`（`AdvisoryTools:158-160`）——三层取最小。

### 4.3 8 个只读工具（`AdvisoryTools`）

`dailyReport`（≤40 天逐日 + 受众/素材分组）｜`countryBreakdown`｜`creativeAndLandingContext`｜
`ruleContext`｜`peerBenchmarks`｜`historicalRuns`（近 5 次审批）｜`scenarioCatalog`｜`simulateBudget`（只算不写库）

### 4.4 检查点（`JdbcCheckpointSaver`）

- 表 `agent_checkpoint(thread_id, checkpoint_id, node_id, next_node_id, state_json)`（**已确认建表**）
- `threadId = "run-" + runId`；写入用 H2 `MERGE ... KEY(thread_id, checkpoint_id)` → 幂等
- 序列化失败降级：逐字段 `String.valueOf` → 再失败写 `{}` 并告警，**不阻塞图**

### 4.5 Copilot 图（`AdOpsCopilotGraph`）

`START → copilot_agent ⇄ copilot_tools → END`，工具轮次上限 6，未配置模型走 `offlineFallbackReply`。

### 4.6 五 Agent 闭环（`FiveAgentOrchestrator`，839 行）

| Agent | 关键实现 |
| --- | --- |
| Monitor | 0–100 健康分 + 阈值告警（CTR<0.5%、CPA 超标、ROAS<1.0） |
| Audience | 高价值人群段排名 + Lookalike 计划 |
| Creative | 3–5 条结构化文案变体（LLM + 确定性 fallback） |
| Bidding | `eCPM = pCTR×pCVR×TargetCPA×1000`，倍率 ×1.3/×0.6，封顶 TargetCPA 80% |
| Optimize | 暂停 <40 分素材 + **`max Σ wᵢ√Bᵢ` 凹目标、λ 二分、clip 到 [lo,hi]**（`:738-796`） |

---

## 五、安全与治理（默认值全部实测确认）

`GET /cms/agent/safety` 实测返回：

```json
{"safetyMode":"COPILOT","killSwitch":false,"dailyBudgetDeltaCap":500.00,
 "usedBudgetDeltaToday":0.00,"remainingBudgetDeltaToday":500.00,
 "maxSingleChangePct":20,"maxLlmCallsPerDay":200,"llmCallsToday":4}
```

`assertExecutionAllowed` 四道闸（`AgentSafetyPolicyService:111-132`）：
① Kill Switch → 423 ② SHADOW 模式 → 409 ③ 单次幅度超 `maxSingleChangePct` → 409（防重置学习期）
④ 今日已提交变动 + 本次 > 日上限 → 409。「今日已提交」从 `optimization_ledger` 按
`action_type='EXECUTE' AND status='COMMITTED'` 聚合**实际变动额**——按落库算，不按建议算。

双人审批（`StaffSecurity:39-55`）：`@ConditionalOnProperty` 才启用；`operator`/`reviewer` BCrypt、
≥12 字符、≤72 字节、互不相同；`/runs/*/review` 仅 REVIEWER；STATELESS + Basic；CSRF 仅对 JSON 写接口豁免。

开放 API 签名（`AuthInterceptor:100-160`）：`TreeMap` 排序 `admin-*` 头 + body MD5，**12 分钟**时间窗，
nonce 一次性（保留 13 分钟，`size>4096` 时清理），`@RootAuth` 收敛超管接口。
**MD5 是存量契约的摘要算法，不是最佳实践**——新接口应上 HMAC-SHA256。

> 本次核验实际用「`admin-name=local-admin` + `data/local-admin.key`」签名打通了 5 个接口，
> 说明这条鉴权链路是通的，不是纸面设计。

---

## 六、业务规则（每条都有代码或库表支撑）

| 规则 | 事实 | 位置 |
| --- | --- | --- |
| 报表渠道未配置 → 建议压回 HOLD | 防「对着空数据编结论」 | `RealisticReportConfigs` 幂等补齐 |
| 归因队列 | `campaignId+cohortDate+ageDay`，重复观测**替换**不累加，≥3 个年龄点且到 D7 才拟合 | `README.md:181` |
| 汇率 | 只影响展示，未录入币种显示「缺少汇率」，不用默认值 | `README.md:183` |
| 实验判定 | `adjusted = p × max(1, arms−1)`、每臂点击与未点击各 ≥10、目标曝光 20000、周期不一致不判胜 | `OverseasEvidenceService:96-108` |
| 本地执行边界 | 只更新本地 TikTok 广告组，其他平台标「待人工处理」，不建外部请求 | `README.md:187` |
| 迁移幂等 | 只覆盖种子语料 `article_id LIKE 'afs-article-%' AND partner='AFS Media'` | `README.md:144` |
| 历史跨度 | 活动窗口分散 2023-03 → 今天，40% 仍在投 | `PlatformEconomics.HISTORY_START` / `windowFor` |

---

## 七、易被追问的真实细节（答对即加分）

| 细节 | 事实 |
| --- | --- |
| 分布式锁 | `DistributeLockService` 是 **DB 行锁表**（`distributed_lock`），`insertIgnore` + `tryLock(key,val,expireAt)` + 比对 val 释放；日志文案残留 `Failed to unlock redis lock` 是历史遗留 |
| nonce 淘汰 | `USED_NONCES.size() > 4096` 时按 `now-13min` 清理再 `putIfAbsent` |
| 进程内缓存 | `CmsCampaignDataService` 用 `volatile` 缓存 `MAX(date)`，**60 秒 TTL** |
| Redis | `pom` 有 starter 但业务零使用；`agent-sim` 显式 `exclude` 两个 Redis 自动装配类 |
| 线程池 | `AsyncTaskConfig`：core 10 / max 30 / queue 100 |
| 索引陷阱 | 日累计台账用 `CAST(created_at AS DATE)=CURRENT_DATE`，**函数包裹导致索引失效**（自己踩的坑） |
| 列表上限 | `listCampaigns` 硬上限 **250**（`Math.min(limit,250)`）——所以 640 不等于能一次拉完 |
| 分页/截断 | 工具 `MAX_DAYS=40`、单次工具输出 6500 字符 |

---

## 八、诚实边界（先想清楚再写简历）

| 别这么说 | 应该这么说 |
| --- | --- |
| 「257 个 Java 文件」 | 「**245** 个主源码文件（另 12 个测试）」 |
| 「32 个 Mapper」 | 「**42** 个 Mapper（dao 32 + publicapi 10）」 |
| 「本地库 36 张表」 | 「**63** 张表」（36 只是 `local/schema.sql` 的 DDL 数） |
| 「一次拉 640 个活动」 | 「库内 640，列表接口硬上限 250」 |
| 「ROAS 提升 40% / CPA −28%」 | 「合成工作区的仿真结果，**不是真实业务效果**」 |
| 「358 次提交都是我写的」 | 「提交历史含脚本生成的功能点记录，不能当个人产出」 |
| 「用了 Redis / RAG / K8s」 | 「都没用；锁是 DB 行锁，记忆是结构化 SQL」 |
| 「自研 Multi-Agent 框架」 | 「基于 LangGraph4j 做编译态编排，自研工具层、护栏与检查点」 |
| 「支撑百万 QPS」 | 「没做过压测；有 78 次运行 / 461 节点 / 127 万 token 的真实执行记录」 |

---

## 九、核验命令（面试前 5 分钟自己跑一遍）

```powershell
# 1. 全量测试（必须全绿，31 个用例）
$env:JAVA_HOME='C:\Users\hwj11\.jdks\ms-17.0.20.1'
mvn -B clean test          # 期望：Tests run: 31, Failures: 0, Errors: 0

# 2. 规模数字（直连 H2）
java -cp h2-2.1.212.jar org.h2.tools.RunScript `
  -url "jdbc:h2:file:./data/cms-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE" -user sa `
  -script q.sql -showResults
#   SELECT COUNT(*) FROM ad_resource;             -> 640
#   SELECT state,COUNT(*) FROM ad_resource GROUP BY state;  -> 320/256/32/32

# 3. 起服务 + 探活
java -jar target/afs-cms.jar --spring.profiles.active=local-cms --server.port=9901
#   GET /cms/console/bootstrap  -> {"mode":"cms","localCms":true,...}
#   GET /cms/agent/status       -> tools[8] + graphs.optimization(mermaid)
```

---

## 十、面试现场「打开哪个文件」

| 问题 | 位置 |
| --- | --- |
| 图怎么编的 | `agent/AdOptimizationGraph.java:150-206` |
| 护栏怎么拦 | `AdOptimizationGraph.java:443-524` |
| 工具怎么定义 | `agent/AdvisoryTools.java:37-156` |
| 检查点怎么存 | `agent/JdbcCheckpointSaver.java:86-98` |
| 安全策略 | `agent/AgentSafetyPolicyService.java:111-132` |
| 双人审批 | `agent/StaffSecurity.java:39-55` |
| 签名算法 | `controller/filter/AuthInterceptor.java:100-160` |
| SSE | `agent/AgentTelemetryService.java:65-130` |
| 模型降级 | `agent/AiModelGateway.java:152-213` |
| 五 Agent | `agent/FiveAgentOrchestrator.java:91-120`、`:738-796` |
| 表结构 | 63 张表，见 §2.2 |
