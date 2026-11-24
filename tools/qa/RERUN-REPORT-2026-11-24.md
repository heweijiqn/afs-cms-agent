# AFS CMS 全量回归测试 — 2026-11-24

- 测试对象：`http://127.0.0.1:9901`（`local-cms`，当前 PID 36712）
- 代码状态：`main` @ `921e8ff` + 工作区未提交的 UI 改进 + 本次测试发现的报表配置修复
- 构建：`mvn -o -DskipTests package`（重新打包并重启）
- 测试时间：2026-11-24 09:38 – 10:05（Asia/Shanghai）

## 结果总览

| 测试面 | 结果 |
|---|---|
| Maven 单元/集成测试 | **31/31 通过** |
| API 只读巡检（72 端点） | **72/72 通过** |
| 修复专项回归（签名/权限/校验/分页/Cohort/五智能体/证据匹配） | **31/31 通过** |
| 执行安全阀（SHADOW/Kill Switch/日上限/单次调幅/指纹/幂等/回滚） | **12/12 通过** |
| 浏览器 UI（13 路由 + 7 智能体页签 + 图表 + 巡检 + 运行详情 + 弹窗生命周期 + 移动端） | **37/37 通过** |
| 16 并发只读压测（240 请求） | 100% 成功；p50 41ms / p95 112ms；funnel p50 19ms、pacing p50 21ms |
| 数据集完整性审计 | **0 个数据问题** |
| 当日 ERROR/WARN 日志审计 | ERROR 0、WARN 0 |

## 新增/重点验证

### 1. 弹窗生命周期（工作区未提交的 UI 改进）

新增 2 项 UI 断言并通过：

- 打开「新建分析」弹窗：`#modal.open=true`，`body.modal-open` 生效，表单存在；
- 按原生 Escape 关闭：弹窗关闭、背景滚动锁释放、弹窗内容清空、标题清空。

说明这批未提交的 `ModalHost.tsx` / `ui.ts` / `business.ts` / `console.css` 改动在浏览器中工作正常；写操作关闭弹窗、结果提示和长结果截断逻辑未发现回归。

### 2. 报表配置改名（本次全量测试新发现）

- 现象：`POST /cms/report/config/edit` 携带 `oldChannel` 做 channel 改名时返回 `200 code=-1 internal error`。
- 根因：MyBatis SQL 中 `COALESCE(NULLIF(?, ''), ?)` 在 H2 2.1.212 下无法推断参数类型（`Unknown data type: "?"`）。
- 修复：在 Controller 中先计算 `oldChannel`（为空时回退为当前 channel），Mapper 改为 `where channel = #{oldChannel}`。
- 验证：`getburnzy / site-overview → site-overview-rerun → site-overview` 改名与恢复均成功。

### 3. 数据与安全

- 数据：240 个活动（TikTok/Facebook/Google/Taboola 各 60），国家全部为 ISO 码；当日 192 行 AdSense 日报；无重复 campaign/channel、无孤儿渠道、无可加总异常。
- 安全：签名 query/body 绑定、12 分钟窗口、nonce 单次使用、root 权限、安全响应头、CSP（0 违规）均通过；非 root 修改安全策略 403。
- 智能体：安全阀测试创建真实 LangGraph run、审批、SHADOW/Kill Switch/日上限/调幅拦截、执行与回滚全部符合预期；恢复后 run 状态 `ROLLED_BACK`。

## 当前工作区

未提交（未推送）：
- 用户/上一轮新增的 UI 改进：`frontend/src/components/ModalHost.tsx`、`frontend/src/modules/business.ts`、`frontend/src/modules/ui.ts`、`frontend/src/styles/console.css`，以及重建后的 `static` 资源；
- 本次测试修复：`ReportDataConfigController.java`、`ReportDataMapper.java`；
- 测试脚本：`tools/qa/ui_test.mjs` 增加弹窗断言。

## 证据

- `tools/qa/out/mvn-test-rerun2.log`
- `tools/qa/out/smoke.json`
- `tools/qa/out/fix-regression-rerun.log`
- `tools/qa/out/safety-rerun.log`
- `tools/qa/out/ui-rerun2.log`
- `tools/qa/out/ui/ui-report.json`
- `tools/qa/out/data-audit.json`
- `tools/qa/load.py` 输出

## 结论

当前工作区代码（含未提交 UI 改进与报表配置修复）通过全量回归，可继续提交/推送。建议将报表配置修复和 UI 改动分别提交，并在提交前再跑一次 `mvn test` + `smoke.py` + `ui_test.mjs`。
