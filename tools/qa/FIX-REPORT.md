# AFS CMS 缺陷修复报告

- 修复时间：2026-11-23 17:17 – 18:06（Asia/Shanghai）
- 修复范围：`TEST-REPORT.md` 中的 P0 / P1 / P2 缺陷
- 服务：已用新构建重启，当前 PID **56968**，`/api/service/health` 正常
- 构建：`mvn -o -DskipTests package`，`target/afs-cms.jar`（197 MB）
- 回归：**Maven 31/31、API 巡检 72/72、修复回归 31/31、UI 35/35**
- 数据库：`data/cms-local`，测试前备份 `tools/qa/out/cms-local-before-cleanup.mv.db`

---

## 一、P0 修复

| 缺陷 | 修复 | 验证 |
|---|---|---|
| P0-1 非 root 用户通过省略 `admin-console` 头执行 root-only 操作 | `AuthInterceptor` 不再用客户端 header 决定 root 校验：`CONSOLE_ADMIN_ACTIONS` 始终要求 root；`@RootAuth` 始终生效 | `alex.chen` 带/不带 `admin-console` 创建规则均 **403**；吸顶测试规则已删除 |
| P0-2 任意登录用户可修改安全策略/Kill Switch | `/cms/agent/safety`、`/safety/kill-switch` 增加 `@RootAuth`；审计 actor 改为真实签名用户 | `alex.chen` → 403；root 修改成功且 `updatedBy=local-admin` |
| P0-3 五智能体提案写入非标准 Plan，审批看板崩溃 | `createProposalFromFiveAgent` 改为复用 `OptimizationWorkflow.rulesOnly()` 生成标准 `Plan`（input/metrics/proposal/context/explanation/findings/evidence/scorecard） | 新提案 run 的标准字段齐全；UI 运行列表正常；`reanalyze` 可执行；回归任务已验证 |
| P0-4 自定义分析套用其他活动 evidence | `loadCampaignEvidence` 仅在 campaignId 为空时才按 channel 回退；campaignId 非空且查不到时返回 `null`，不再覆盖 `resolvedId` | 自定义 `TT_US_HomeSecuritySystems_Conv_250_QA` 的 `plan.evidence=null`，不再出现 `cms:1850…` 的 14 天数据 |

---

## 二、P1 修复

### 2.1 鉴权与协议

- **签名绑定 query + JSON body**：`path[?query]&method&sorted(admin-* headers)&secret&`；JSON POST/PUT/PATCH 必须带 `admin-body-md5`，服务端缓存 body 并校验。
- **签名窗口** 从 12 小时改为 **12 分钟**。
- **nonce 单次使用**：12 分钟内重复 nonce 直接 401。
- **前端与测试客户端同步升级**：`frontend/src/modules/api.ts`、`tools/qa/client.py`、Maven 测试签名 helper 均已更新。
- **JSON body 上限 10MB**：`CmsJsonBodyFilter` 对超限 JSON 返回 413。

> 注意：这是**破坏性协议变更**。任何外部脚本/客户端调用 `/cms/**` 都需要同步实现新的签名规则和 body hash。

### 2.2 异常与审计

- `ResponseAdviser`（CMS 与 public API）新增统一处理：参数校验、缺参、缺头、类型不匹配、JSON 解析失败、方法不支持、上传超限，分别返回 400/405/413，不再统一 `200 code=-1 "internal error"`。
- 新增 `Actors`：优先从 `KiwiContext`（签名验证后的真实用户）取 actor，`Principal` 兜底；`OptimizationController`、Copilot、扩展、Ops、Overseas 全部切换。
- 文章/活动的审核结果新增 `auditor`、`audit_time` 字段并落库。

### 2.3 统计口径与看板

- `CampaignSummary` 新增 `dataDate`（工作区最新报表日，缓存 60s）；前端活动选择器、巡检表头改为“**最新日报花费（2026-11-20）**”，与 14 天分析口径区分。
- `Pacing` 无当日观测时返回 `dataAvailable=false`、`firewallHealth=NO_DATA`，不再显示 `SAFE`；告警扫描返回 `dataAvailable=false` 和“今日尚无平台观测数据”提示。
- UI `FIREWALL_TONE` 增加 `NO_DATA`，巡检表头/导出列同步改名。

### 2.4 海外与自动化

- Cohort 查询窗口改为 `lookback + max(lookback,30)` 天，`days=30` 能看到已成熟的 2026-10-20 D30 队列；返回 `windowDays/horizonDays`。
- 自动化规则支持 `REDUCE_BUDGET`（映射为负 `budgetDeltaPct`）及 `ADJUST_BUDGET` 负值（±50%），“夜间低 ROAS 降价”规则可创建。
- 本地化生成：自动合规检查改用确定性关键词规则，一次生成请求只调用 **1 次**付费模型；补充德语/法语/西语绝对化承诺词。
- 非法草稿状态（创意/本地化）改为 400 拒绝，不再静默发布为 `PUBLISHED`。
- 告警规则/自动化规则的非法枚举改为 400 拒绝，不再静默降级。

### 2.5 CMS 数据一致性

- 活动克隆不再复制 `channelId`/`landPageUrl`，克隆后必须重新绑定独立渠道。
- 活动创建：root 视为 `autoCampaign`，自动过审分支正常执行；非自动分支返回 `adResourceId`。
- 活动审核校验 `channelId` 必须存在于 `adsense_channel`，拒绝孤儿引用。
- `pubId/list` 改为读取真实 `adsense_account` 数据；新增 pubId 格式与站点校验。
- 分页：`maxIndex=ceil`、`page.size=clamp(1..100)`、offset/查询使用 clamp 后的 size。
- 素材编辑不再丢 type/dimension/ratio/cover/url；素材分类改用逗号分隔，修复 `home-security` 被拆成 `["home","security"]`。
- 广告文案 `count` 改为与列表一致的 `LIKE` 匹配。
- 报表配置支持 `oldChannel` 改名；失败写入改为 `code=-1` 而不是假成功。
- 空搜索串返回空列表，不再导出全表。
- `region/list` 修正为 `regionCode=ISO`、`name=中文国名`。
- TikTok smart-creative placement 校验方向修正。
- 素材/文案/语言/分类/配置等 8 处“更新失败但 code 0”改为错误返回。

### 2.6 前端/安全

- CSP 增加 `style-src 'self' 'unsafe-inline'`，内联样式不再被拦截；全流程 **0 次 CSP 违规**。
- 新增 `SecurityHeadersFilter`：CSP、X-Frame-Options=DENY、X-Content-Type-Options=nosniff、Referrer-Policy、Permissions-Policy、COOP。
- 移除 CMS/Agent 控制器的 `@CrossOrigin(origins="*")`；跨域请求不再返回 `Access-Control-Allow-Origin: *`。
- 运行历史 `/cms/agent/runs` 支持 `limit`（默认 50，最多 200），前端请求 100 条。
- 登录页 `sarah.lin`/`david.wu` 角色标签修正。
- `formatActor` 不再把 `local-admin` 显示为 `admin`。
- `/error` 加入本地公开路由；`/cms/console/local-api` 新增独立控制器，public 上报同时镜像到 `local_api_event`。
- `Boring Life` / `example.com` 假数据已从 `ad_resource` 删除（campaigns 241 → 240），`getCusChannelId` ERROR 日志根因消除。
- 种子告警事件的虚假 `DELIVERED` 状态改为 `SEED_SIMULATED`（31 条）。

### 2.7 性能

- `funnel`/`pacing` 增加 30 秒结果缓存（深拷贝返回，避免调用方修改缓存）。
- 16 并发 240 请求实测：`funnel` p50 **2,866ms → 13ms**，`pacing` p50 **1,295ms → 11ms**；整体 p95 从 2,999ms 降至 180ms。

---

## 三、验证结果

| 验证 | 结果 |
|---|---|
| Maven 测试（含智能体集成/安全/海外） | **31/31 通过** |
| API 只读巡检（72 个代表性端点） | **72/72 通过** |
| 修复回归（签名、权限、校验、pacing、分页、Cohort、预算下调、五智能体、证据匹配） | **31/31 通过** |
| 浏览器 UI（13 路由 + 7 智能体页签 + 图表/扫描/运行详情/移动端） | **35/35 通过**，CSP 违规 0 |
| 安全头 | CSP / XFO / XCTO / Referrer / Permissions / COOP 均存在 |
| 跨域 | `Origin: https://evil.example` 不再获得 `ACAO: *` |
| 数据 | 240 个活动、12 条规则、48 条 run、安全策略 COPILOT/未熔断 |

证据：
- `tools/qa/out/mvn-test-final4.log`
- `tools/qa/out/smoke.json`
- `tools/qa/out/fix-regression.json`
- `tools/qa/out/ui/ui-report.json`
- `tools/qa/out/fix-regression3.log`

---

## 四、仍需说明的事项

1. **`/media/**` 仍为公开读取**：文章/图片资源本身需要公开访问；路径穿越已被阻断。若需要私有化，需要引入签名 URL，属于产品决策。
2. **报表配置“改 channel 名”**：后端已支持 `oldChannel` 字段，但当前 UI 编辑表单未提交该字段；如需在 UI 中改名，还需补一处表单字段（低风险）。
3. **测试 run 保留**：48 条 run 中约 20 条为多轮测试生成（含 QA 命名），系统没有删除 run 的 API；不影响功能，需要时可按备份/数据库清理。
4. **外部客户端需升级**：新的签名协议和 `admin-body-md5` 是破坏性变更；浏览器前端和 `tools/qa/client.py` 已升级，其他脚本需要同步。
5. **QA 数据**：ECB 汇率、Cohort、账户/落地页观测、德语本地化草稿为测试补充数据，保留用于演示；测试前数据库备份在 `tools/qa/out/cms-local-before-cleanup.mv.db`。

---

## 五、主要改动文件

- 鉴权/协议：`AuthInterceptor.java`、`CmsJsonBodyFilter.java`、`SecurityHeadersFilter.java`、`frontend/src/modules/api.ts`、`tools/qa/client.py`
- 安全策略/actor：`OptimizationController.java`、`Actors.java`、`AgentCopilotController.java`、`AdAgentExtensionsController.java`、`AdOpsInsightsController.java`、`AdOverseasOpsController.java`
- 五智能体/证据：`AdAgentExtensionsService.java`、`CmsCampaignDataService.java`
- 异常处理：`ResponseAdviser.java`（CMS + publicapi）
- 海外：`OverseasDataService.java`、`AdOverseasOpsService.java`
- CMS：`AdResourceController.java`、`AdMaterialController.java`、`AdTextMapper.java`、`AdsenseAccountController.java`、`ReportDataConfigController.java`、`CountryController.java`、各分页控制器
- 前端：`agent.ts`、`ops-insights.ts`、`LoginView.tsx`、`static/index.html`
- 测试：`ConsoleAuthorizationTest.java`、`LocalConsoleIntegrationTest.java`、`LangGraphCommercialAgentTest.java`、`OverseasOperationsTest.java`、`AdOpsInsightsTest.java`
- 数据/配置：`local/schema.sql`（auditor 字段）、`data/cms-local`（删除假活动、修正告警状态）
