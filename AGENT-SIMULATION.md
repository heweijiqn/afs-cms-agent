# AFS CMS 管理后台与智能体

智能体代码位于 `src/main/java/com/afs/agent`，测试位于 `src/test/java/com/afs/agent`。原 CMS 和智能体使用同一个 Maven 构建、`com.afs.Application` 启动类和 `target/afs-cms.jar`。

默认使用 `local-cms`。在 `afs-cms` 目录运行 `./Run-LocalCms.ps1`，一个进程在本机 `9901` 提供 CMS、API 和控制台，共用 `data/cms-local.mv.db`。首次启动会生成本地账号 `local-admin`，签名密钥位于 `data/local-admin.key`。

前端跟随同一个 JAR 打包，无需 Node 启动。启动模拟后，浏览器打开 <http://127.0.0.1:9903/>。后台包含运营总览、广告活动、逐日报表、规则明细和站点配置，智能体工作台包含新建分析、审批、预算试算和审计记录。模拟模式的后台页面读合成样例。

管理控制台入口为 `/console`。业务接口使用 `/cms/**` 和 `/api/**`，API 服务健康检查使用 `/api/service/health`。单独启动 `agent-sim` 时，业务表单只预览请求，智能体记录写入独立的模拟数据库。

「内容管理」提供文章、广告素材、广告文案及文章分类查询。「账号与渠道」提供 AdSense 账号和渠道查询。管理员还可查看操作员和广告账号目录、维护广告文案。报表渠道配置位于「站点配置 → 报表配置」。模拟模式展示合成样例，写操作在原 CMS 登录后使用。

「业务操作」集中提供广告活动创建和审核、TikTok 活动编辑、文章发布流程、规则管理、素材批量导入、文件上传、账号和渠道维护、多维报表、用户反馈及资料查询。活动、规则和各管理目录也提供行内操作，能用列表字段填入表单。生产模式需要有权限的账号，影响投放或删除数据的表单需要手动确认。模拟模式只验证参数和请求格式，智能体有独立的审批和预算试算流程。健康检查、定时任务和开放 API 由现有接口提供。

## 导入历史 CMS 日志

在 `afs-cms` 目录执行以下命令，随后在模拟控制台打开「投放报表 → 查看历史运行日志」。

```powershell
python .\tools\import-log-report.py 'C:\path\to\afs-cms.zip'
```

导入程序只统计主日志和轮转主日志中带时间戳的记录，汇总 DEBUG、INFO、WARN 和 ERROR 等级。日志正文与凭据不会写入报表产物。汇总文件 `data/imported-log-report.json` 留在本地，不进入 JAR 或 Git。运行日志汇总与花费、收入、转化使用不同视图。

## 导入归档数据库

从 `afs-cms` 目录使用 JDK 17 运行。脚本默认使用 Maven 本地缓存中的 H2。

```powershell
python .\tools\import-archive-database.py 'C:\path\to\afs-cms.zip'
```

如要同时读取 `afs-api.7z` 中的应用快照，可追加 `--api-archive 'C:\path\to\afs-api.7z'`。导入还会读取原 CMS 归档内的文章资料。历史投放字段出现在「投放报表」，原始文章出现在「内容管理」，导入的应用出现在「账号与渠道 → 归档应用」。

「渠道密钥」用于加密保存本地凭据，密钥文件和数据库分别存放。列表接口只返回渠道和别名，保存不会启用在线连接。「API 数据管理」可维护本地站点及报表配置，查看通过 CMS 下的 `/api/feedback/add`、`/api/keyword/report` 和 `/api/ip/report` 收到的本地上报。

数据库快照中的广告统计、应用、广告位、用户和应用访问记录，以及分类 SQL 中的文章分类和子分类，写入独立的 `data/imported-business.mv.db`。导入保留各表的业务记录，用户密码、应用密钥和原数据库的登录信息不入库。重复导入同一份文件会按记录主键合并。在「投放报表 → 查看归档数据」或左侧「归档数据库」可查，数据不会进入现有 CMS 用户表或智能体流程。

## 启动模拟接口

使用 JDK 17，在 `afs-cms` 目录执行：

```powershell
mvn clean package
java -jar target/afs-cms.jar --spring.profiles.active=agent-sim
```

Windows 也可以从工作区根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\afs-cms\Run-AgentSimulation.ps1
```

`agent-sim` 是 Spring 的运行配置。该配置必须单独启用，监听本机端口 `9903`，使用 H2 和合成数据。它不会加载原 CMS 的业务定时任务、投放连接器、MyBatis 或 Redis。模拟记录存放在 `afs-cms/data`（从该目录启动时）。退出使用 `Ctrl+C`。

## 验证工作流

```powershell
$base = 'http://127.0.0.1:9903/cms/agent/runs'
Invoke-RestMethod "$base/scenarios"
$headers = @{'Idempotency-Key' = [Guid]::NewGuid().ToString()}
$run = Invoke-RestMethod "$base/scenarios/healthy" -Method Post -Headers $headers -ContentType application/json -Body '{}'
$id = $run.id
Invoke-RestMethod "$base/$id/review" -Method Post -ContentType application/json -Body '{"decision":"APPROVED","note":"Synthetic scenario verified"}'
Invoke-RestMethod "$base/$id/simulate" -Method Post -ContentType application/json -Body '{"currentBudget":100}'
Invoke-RestMethod "$base/$id/audit"
```

`healthy` 场景的 ROAS 为 1.70，试算后的预算为 USD 110.00。另有 `underperforming` 和 `low-volume` 场景。可用 `POST /cms/agent/runs` 提交自定义合成汇总数据，入参示例在 `src/main/resources/agent/simulation/example-run.json`。自定义汇总数据缺少日度证据时，预算规则不建议放量。

智能体基于 **LangGraph4j 1.5.14 + LangChain4j 1.0.1** 构建，采用编译态 `StateGraph` 编排与 JDBC 检查点持久化（`agent_checkpoint`），支持两种分析编排模式：

- **标准 ReAct 图（`STANDARD`）**：`intake_context → strategy_planner ⇄ tool_executor → guardrail_critic ⇄ self_reflect → finalize_plan`。
- **深度多智能体协作图（`DEEP`）**：`intake_context → research_specialist ⇄ tool_executor → strategy_planner ⇄ tool_executor → guardrail_critic ⇄ self_reflect → finalize_plan`，由归因研究专家先采集多维证据再由策略师输出方案。

创建 run 时先自动读取真实 CMS 日度报表、国家拆分、创意与规则上下文并落库（`explanation.source` 为 `PENDING`），随后异步流式执行 LangGraph4j 图并通过 SSE（`GET /cms/agent/runs/{id}/stream`）实时推送节点进度。模型支持调用 8 个只读 CMS 工具（`dailyReport`、`countryBreakdown`、`creativeAndLandingContext`、`ruleContext`、`peerBenchmarks`、`historicalRuns`、`simulateBudget`、`scenarioCatalog`），并内置 `guardrail_critic → self_reflect` 自检反思修正循环与硬性规则护栏。

工作台同时提供：
- **AI 投放副驾驶（Copilot）**：基于 LangGraph4j ReAct 图 + SQL 持久化多轮记忆（`/cms/agent/chat/**`），支持全盘概览、异常巡检、活动深挖与一键发起正式审批工单。
- **全盘异常巡检（`POST /cms/agent/scan`）**：自动识别严重亏损、回收偏低、放量机会与未绑规则的高消耗活动。
- **商用可观测性（`GET /cms/agent/telemetry` / `GET /cms/agent/runs/{id}/trace`）**：记录每次图节点的耗时、Token 消耗、工具链与检查点，并支持配置主备模型自动故障转移。

配置入口是「渠道密钥 → AI 平台」（支持主模型、备选降级模型与 Temperature），也可用 `AFS_AGENT_AI_BASE_URL` / `AFS_AGENT_AI_API_KEY` / `AFS_AGENT_AI_MODEL` / `AFS_AGENT_AI_TIMEOUT_SECONDS` 或 `CMS_AGENT_LOCAL_MODEL_PORT` 配置；未配置模型时自动降级为离线规则模板，并在已存在记录上提供「AI 重新分析」按钮一键升级。

「验证连接」现在会真的发一次 `chat/completions`，返回状态码、耗时和模型回复，避免出现「模型列表能通但分析全部降级」的误判。

如需验证双人审批，设置 `CMS_AGENT_REQUIRE_STAFF_LOGIN=true`、`CMS_AGENT_OPERATOR_PASSWORD` 和 `CMS_AGENT_REVIEWER_PASSWORD`。使用 HTTP Basic 账号 `operator` 创建并试算，`reviewer` 审批。本地模拟仍使用合成数据。

## 原 CMS 连接器

为让整个 CMS 一起编译，按现有调用点补齐了此前缺失的 HTTP 和阿里云 OSS 服务。原 CMS 的 OpenAI HTTP 接口读取 `cms.connectors.openai-api-key`。OSS 读取 `cms.connectors.oss` 下的 `endpoint`、`access-key-id`、`access-key-secret`、`bucket` 和 `public-base-url`。这些连接器没有在真实环境验证。

