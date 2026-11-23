# AFS CMS 全量测试报告

- 测试对象：`http://127.0.0.1:9901`（profile `local-cms`，H2 文件库 `data/cms-local`，`target/afs-cms.jar`）
- 测试时间：2026-11-23 16:35 – 17:15（Asia/Shanghai）
- 服务进程：测试结束后已重启，当前 PID 780，`/api/service/health` 正常
- 测试方式：真实 HTTP 调用（签名鉴权）× API 全量巡检 × 智能体端到端 × 无头 Chrome UI × 只读并发压测 × H2 数据核对
- 自动化客户端：`tools/qa/client.py`（按 `AuthInterceptor` 签名协议实现）
- 证据目录：`tools/qa/out/`（各阶段 JSON、截图、日志、数据库备份）

---

## 一、结论

系统主链路是**可用**的：CMS 内容/活动/规则/报表、智能体 STANDARD/DEEP 分析、SSE、审批指纹、预算试算、幂等执行、一键回滚、护栏熔断、海外 FX/Cohort/本地化、权限负向用例、SQLi/XSS/路径穿越防护均通过。

但不能判定为“可上线”：发现 4 个 **P0** 级问题，其中最严重的是**非 root 用户可通过省略 `admin-console` 请求头执行 root-only 操作**，以及**任意登录用户可改全局安全策略/熔断开关**。另有约 20 个 P1/P2 问题，集中在统一异常处理、审计归属、统计口径、CSP/安全响应头、分页与数据一致性。

| 测试面 | 结果 |
|---|---|
| Maven 既有测试（含智能体集成测试） | **31/31 通过** |
| API 只读巡检（72 个代表性端点） | 67/72 返回业务成功；5 个为契约/文档问题或预期 404 |
| 智能体核心（153 项检查：STANDARD/DEEP、SSE、审批、场景、Copilot、扩展） | 124/153；7 类确认缺陷 |
| CMS 核心与控制台（36 项检查） | 21 通过 / 12 部分通过 / 3 失败；24 条缺陷（1 HIGH / 12 MEDIUM / 11 LOW） |
| 海外运营与安全（161 项检查） | 140/161；16 条缺陷（2 High / 8 Medium / 6 Low） |
| 浏览器 UI（13 个路由 + 7 个智能体页签） | 34/35；唯一失败为 CSP 拦截内联样式 |
| 只读并发（240 请求 / 16 并发） | 100% HTTP 成功；`funnel` p50 2.9s、`pacing` p50 1.3s，存在性能瓶颈 |
| 执行安全阀 | **12/12**（SHADOW、Kill Switch、日预算上限、单次调幅、指纹、幂等、回滚） |

---

## 二、真实测试数据集

没有使用“1 美元、100 次点击”这类一眼假的数据。主数据集来自系统内置的 platform-authentic 工作区，并补充了真实结构的运营数据：

- **241 个活动**：TikTok 60 / Facebook 61 / Google 60 / Taboola 60；覆盖 US/GB/CA/AU/DE/FR，真实垂直（家装、保险、金融、健身、太阳能、牙科、教育等）。
- **14 天逐日报表**：2026-11-07 – 2026-11-20，花费/收入/展示/点击/转化逐日可加总。
- **192 个 AdSense 渠道**、60 个 TikTok 广告组、12 条投放规则、12 个广告账户、13 个运营账号。
- 核心样本 `TT_US_FoldableTreadmill_RSOC_001`：14 天花费 **4,777.83 USD**、收入 **7,621.28 USD**、ROAS **1.5951**、转化 **2,474**、展示 **672,687**、当前日预算 **400 USD**；逐日数据与汇总值完全对账。
- 补充的真实结构数据：ECB 日期汇率（EUR/GBP/JPY/CNY，23 天）、D0–D30 Cohort、账户余额/拒登/过期快照、落地页 TTFB/CVR 观测、德语本地化草稿、真实英文文章与素材。

> 发现一条历史遗留假数据：活动 `Boring Life`（`example.com/landing`、国家“澳大拉西亚”、0 花费、创建于 11-19）。它会持续触发 `getCusChannelId site=example` ERROR 日志。测试未擅自删除，建议清理。

---

## 三、P0：必须立即修

### P0-1 非 root 用户可绕过控制台管理员校验（权限提升）

- 位置：`src/main/java/com/afs/controller/filter/AuthInterceptor.java:61-63`
- 根因：`rootRequired = ... || (console && CONSOLE_ADMIN_ACTIONS.contains(path))`。这批“控制台管理员动作”方法本身没有 `@RootAuth`，只有请求头 `admin-console: 1` 时才检查 root；省略该头即完全跳过 root 校验。
- 复现（已最小化验证，未留数据）：
  - `alex.chen` + `admin-console: 1` → `POST /cms/ad/resource/rule/add` = **403 权限不够**
  - `alex.chen` 省略 `admin-console` → 同一请求 = **200 code 0**，实际创建规则 id 256（已删除）
- 影响：普通优化师可创建/修改/删除规则、语言、AdSense 渠道、素材/文案，覆盖 `rule/*`、`lang/*`、`adsense/*`、`material/del`、`text/del` 等。
- 建议：这些方法统一加 `@RootAuth`；不要用客户端可控 header 决定权限。

### P0-2 全局安全策略与 Kill Switch 无管理员鉴权

- 位置：`src/main/java/com/afs/agent/OptimizationController.java:145-160`
- 现象：`alex.chen` 调用 `POST /cms/agent/safety`、`POST /cms/agent/safety/kill-switch` 均 **200**，可把模式改为 `AUTONOMOUS`、调高日预算变动上限、关闭/开启全局熔断。
- 附带证据：改完之后 `updatedBy` 记录为 `admin`（见 P1-3）。
- 影响：任何登录用户可放开预算写入或熔断全站投放。
- 建议：两个接口加 `@RootAuth`，并把审计 actor 落到真实签名用户。

### P0-3 五智能体提案写入非标准 Plan，导致“分析与审批”整页崩溃

- 位置：`src/main/java/com/afs/agent/AdAgentExtensionsService.java:709+`（`createProposalFromFiveAgent`）
- 现象：`POST /cms/agent/ext/five-agent/propose` 直接插入扁平 plan：
  `campaignId / campaignName / channel / currentDailyBudget / proposedDailyBudget / action / changePercent ...`
  而 `optimization_run.plan` 的消费者期望 `input / metrics / proposal / context / explanation / findings / evidence / policyVersion / scorecard`。
- 复现：run `31956dfc…`（已清理）
  - `POST /cms/agent/runs/{id}/reanalyze` → `200 code -1 internal error`
  - 浏览器 `#agent → 分析与审批`：运行列表变成 `Cannot read properties of undefined (reading 'action')`，0 条记录，详情停留在“正在读取分析详情…”
- 影响：一条五智能体提案会污染整个审批看板；`simulate` 读取 `changePercent=0`，`execute` 读取空 campaignId，形成“看似可审批、实际不可执行”的坏工单。
- 建议：改为复用 `OptimizationWorkflow.Plan` 序列化；对历史坏 plan 做兼容读取/迁移；列表渲染加防御。

### P0-4 自定义分析静默套用其他活动的证据

- 位置：`src/main/java/com/afs/agent/CmsCampaignDataService.java:140-170`
- 现象：提交自定义 `campaignId=TT_US_HomeSecuritySystems_Conv_250`、`channel=4630219184`（该 channel 属于 `1850127310500007919`）时，`campaignId` 查不到，代码按 channel 回退命中另一活动，并把 `resolvedId` 改成该活动。
- 证据：run `8806455e…` 的 `plan.input.campaignId` 是自定义值，但 `plan.evidence.scenario="cms:1850127310500007919"`、`observedBudget=400`、14 天日数据全部来自另一活动。
- 影响：AI 解释、预算试算、护栏判断会基于错误活动；审批人无法从工单看出数据错配。
- 建议：campaignId 未命中时要么返回 404，要么明确标记 `evidenceMatchedBy=CHANNEL` 并在 UI/审批中提示；不要覆盖 `resolvedId`。

---

## 四、P1：上线前应修

| # | 问题 | 证据/位置 | 影响 |
|---|---|---|---|
| P1-1 | **签名不绑定 query/body，12 小时重放窗口，nonce 不校验** | `AuthInterceptor.java:106,127`；同一签名换 query/body 仍 200；11h 前时间戳仍 200，13h 才 401 | 抓到一个签名即可在 12h 内改参数重放（含 POST body） |
| P1-2 | **校验失败统一 HTTP 200 + `code=-1 "internal error"`** | 缺 `Idempotency-Key`、坏币种、负预算、非法 UUID、malformed JSON、20MB body 全部如此；`ResponseAdviser.java:69-81` | 前端只能显示“internal error”；监控/网关无法按 4xx 统计；日志被 ERROR 噪声淹没 |
| P1-3 | **审计 actor 全部错** | review/ledger/safety=`admin`，chat=`local-operator`，brief/draft=`operator`；`OptimizationController.java:730`、`AgentCopilotController.java:295`、`AdAgentExtensionsController.java:122` | 无法追责；和 P0-2 叠加后，普通用户改安全策略会被记成 admin |
| P1-4 | **活动列表与分析口径不一致且无标签** | `GET /cms/agent/campaigns` 用 `ad_resource.ad_spend`（最后一天）；分析用 14 天 evidence。样本 006：列表 308.12/ROAS 1.7866，分析 4,476.67/1.6754 | 同一活动两个页面数字差 10 倍，用户会误判 |
| P1-5 | **CSP 拦截内联样式** | `src/main/resources/static/index.html:7` 只有 `default-src 'self'`；概览 Cohort 面板 `margin-top:20px` 计算值 0、`font-size:13px` 计算值 18px、`color:#087c69` 丢失；全流程 21 次违规 | 海外 Cohort/本地化/巡检面板布局与配色退化 |
| P1-6 | **无 HTTP 安全响应头** | `/console`、`/cms/**`、`/api/**` 均无 `X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy`；CSP 仅是 meta | 点击劫持、MIME 嗅探风险；meta CSP 无法设置 `frame-ancestors` |
| P1-7 | **无当日数据时 Pacing 显示 0 + SAFE，告警永远不触发** | `GET /cms/agent/ops/pacing` → `items:[], firewallHealth:"SAFE"`；`POST /alerts/scan` 两次 `newlyTriggered:0`，而库内有 64 个 TikTok 活动/16,364 USD 花费 | 资金防火墙在最需要告警的“数据未到/断更”场景给出安全假象 |
| P1-8 | **克隆活动复制 channelId/landing URL** | `AdOpsInsightsService.java:587-615` | 两个活动共用一个 AdSense channel，收入归因冲突；源活动归档后 channel 无法释放 |
| P1-9 | **非法枚举静默降级/失败开放** | 告警规则 `AD_SPEND/>/HIGH/TikTok` 被存成 `ROAS/LT/WARN/FEISHU`；创意草稿 `BOGUS_STATUS` 被存成 `PUBLISHED`；`safePct` 静默夹到 ±50 | 配置看似成功、实际含义完全不同；发布动作不可逆 |
| P1-10 | **本地化生成 1+N 次付费模型调用，单次 233.9s** | `generateLocalizedCreatives` 1 次生成 + 3 次合规审计；`/overseas/localization/generate` 实测 233,937ms | 同步请求易超时；成本不可控 |
| P1-11 | **Cohort days=30 看不到已成熟 D30 队列** | 窗口按 cohort_date 截断而非 age；`days=30` 隐藏 2026-10-20 的 D30 队列，`days=90` 才可见 | 默认视图无法算 D30/payback，长周期分析结论缺失 |
| P1-12 | **预算下调规则无法创建** | `REDUCE_BUDGET` → 400“不支持的规则或动作”；`ADJUST_BUDGET` 负 delta → 400（只允许 0..+50%） | “夜间低 ROAS 降价”类规则在 API 层不可表达 |
| P1-13 | **分页元数据错误** | `maxIndex=floor(total/pageSize)` 隐藏最后一页（site=bitsxspace：21 行、maxIndex=2，第 3 页仍有数据）；`size` 恒为 `max(10,pageSize)`；部分端点 pageSize=500 绕过 100 上限 | 前端漏数据；API 契约不可信 |
| P1-14 | **活动创建返回空 data，root 自动过审不执行** | `POST /cms/ad/resource/add` → `{code:0,data:{}}`；DB `autoCampaign=false`，profile 却返回 `true` | 调用方拿不到 adResourceId；误以为已自动过审 |
| P1-15 | **活动审核接受不存在的 channelId；pubId 选择器不可用** | PASS 审核接受 `9999999999`（adsense_channel 0 行）；`/cms/adsense/account/pubId/list` 返回 5 个空 `partner-` | 孤儿渠道引用；渠道选择流程断裂 |
| P1-16 | **素材/文案/报表配置数据丢失或假成功** | 素材 edit 丢 type/dimension/ratio/cover/url；`/cms/ad/text/list` 1 行但 `totalSize:0`；report config 改名返回 code 0 但未生效 | 运营改了等于没改 |
| P1-17 | **性能：funnel/pacing 在并发下显著退化** | 16 并发 40 次：funnel p50 2,866ms（单发约 500ms），pacing p50 1,295ms（单发约 220ms） | 概览页并发打开时体验差；需缓存/合并查询 |

---

## 五、P2：建议排期

- 删除/onOff/rule-del 对不存在 id 返回 `code 0`（假成功）；`ResponseUtils.result("更新失败")` 仍是 code 0。
- 搜索接口空查询串会导出全表（444 篇文章/246 活动），无分页。
- `GET /cms/region/list` 把 `regionCode` 与 `name` 互换。
- 素材分类以 `-` join、列表再按 `-` split，`home-security` 回读成 `["home","security"]`。
- TikTok smart-creative placement 校验写反（`containsAll` 方向错），仅 PANGLE 也能过。
- 文章重复审核/未知 id 只给“更新失败”或 NPE；文章/活动审核没有 actor 字段。
- `/cms/console/local-api` 在 local-cms 未注册（404/405），`LocalContentApi` 未被 `@Import`/扫描；文档中的“API 数据管理”不可用（模拟模式设计如此，但文档未注明）。
- `/media/**` 无需鉴权（路径穿越已阻断）；`/error` 被 AuthInterceptor 拦截返回 401。
- 运行历史 `/cms/agent/runs` 固定 `LIMIT 30`，无分页/搜索；当前 38 条时 8 条不可见。
- 登录页快捷账号角色标签与种子数据相反：`sarah.lin` 标成 Google（实际 Meta），`david.wu` 标成 Meta（实际 Google）。
- `Boring Life`/`example.com` 假数据导致每次渠道映射都打 ERROR；建议清理。
- CORS 对签名 CMS API 返回 `Access-Control-Allow-Origin: *`（无 Cookie 场景风险有限，但应收紧）。
- 告警事件种子数据 `deliveryStatus=DELIVERED`，但 webhook 是假地址、并未真实投递。

---

## 六、已通过的关键能力（回归基线）

- **鉴权负向**：缺签名/错签名/未知用户/错 key → 401；13h 时间戳 → 401；非 root 读 root 接口 → 403。
- **CMS**：文章 add→audit→archive→del 全生命周期；规则 CRUD+bind/unbind；活动筛选/排序；图片上传真 PNG 可回读，33MB/文本/空文件正确 400；报表 recalculate 连跑两次 192 行完全一致（幂等，无漂移）。
- **智能体**：STANDARD/DEEP 均返回真实 14 天 evidence；SSE 节点 + done；trace 8–11 节点/9–11 checkpoint；审批版本+指纹双校验；试算/执行/回滚账本；同一幂等键重放安全；新键重复执行 409；回滚恢复 400 USD。
- **护栏**：SHADOW 409、Kill Switch 423、日累计超限 409、单次调幅超限 409、指纹不符 409；恢复策略后执行成功，被拦截尝试不落账本。
- **海外**：ECB 日期汇率精确换算（USD×0.94=EUR）；缺失币种明确 400 不回落默认值；Cohort MERGE 幂等、未成熟拒绝并原子回滚、D7/D30 预测门槛正确；TTFB/CVR 相关、账户 STALE/LOW_BALANCE/RESTRICTED 标记；德语生成 + 合规雷达命中“garantiert 100% Heilung”。
- **安全**：SQLi 字符串无注入、XSS 仅作文本返回、路径穿越 404/400、actuator/H2 控制台未暴露。
- **并发**：10 并发 review 仅 1 成功 9×409；5 并发 simulate 仅 1 成功 4×409。
- **UI**：13 个路由、7 个智能体页签全部渲染；概览 Canvas 图表实际绘制 65,987 像素；Ctrl+K 命令面板开关正常；390px 移动端无横向溢出。

---

## 七、测试数据清理与服务状态

- 测试结束后停止服务，备份数据库到 `tools/qa/out/cms-local-before-cleanup.mv.db`，清理后重启：
  - 删除五智能体坏 run `31956dfc…` 及其 audit/ledger/checkpoint/trace；
  - 删除 QA 残留：AdSense 账户 `garbage/qa-site2`、dayparting `platform:QA_TEST` 与 `global:default-untouched`、lang 186/189、category/category_child 与 material category 的 `qa-home-security`、report config 273、创意草稿 7/8/9、QA 日报 brief 2。
- 当前基线：**241 个活动、12 条规则、3 个 dayparting scope、38 条智能体 run、安全策略 COPILOT/未熔断**；服务 PID 780，健康检查正常。
- 保留的可复用测试数据：ECB 日期汇率、Cohort、账户/落地页观测、德语本地化草稿（均无删除 API）。若需要完全回到测试前状态，用上述备份恢复。

---

## 八、建议修复顺序

1. **P0-1/P0-2**：权限与安全阀（先堵住，改动小、风险最低）。
2. **P0-3/P0-4**：五智能体 plan 统一 + 证据匹配显式化（否则审批数据不可信）。
3. **P1-1/P1-2**：签名覆盖 body/query + 统一 4xx 异常处理（安全与契约基础）。
4. **P1-3**：把 `admin-name` 写入 `Principal`/审计上下文（一次修复所有 actor）。
5. **P1-5/P1-6**：CSP 增加 `style-src 'self' 'unsafe-inline'`（或移除内联 style），补安全响应头。
6. **P1-4/P1-7/P1-9**：统一统计口径、NO_DATA 状态、拒绝非法枚举。
7. 其余按上表排期。

---

## 九、证据索引

- 智能体核心：`tools/qa/out/agent-core.json`、`agent-core.md`
- CMS 核心：`tools/qa/out/cms-core.json`、`cms-core.md`
- 海外与安全：`tools/qa/out/overseas-sec.json`、`overseas-sec.md`、`overseas-sec_*.json`
- UI：`tools/qa/out/ui/ui-report.json` 与 24 张截图（`route-*.png`、`agent-tab-*.png`、`mobile-overview.png`）
- 五智能体坏工单：`tools/qa/out/five-agent-ui-break.json`
- 端到端与安全阀：`tools/qa/out/agent-flow.log`、`safety.json`、`safety.log`
- 数据审计：`tools/qa/out/data-audit.json`
- 只读并发：`tools/qa/load.py` 输出
- Maven 测试：`tools/qa/out/mvn-test.log`
