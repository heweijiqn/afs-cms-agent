# 岗位要求对照（全栈工程师 / SaaS + AI 应用）

本文件把岗位描述的每一条要求，映射到仓库里**可运行、可验证**的实现，并明确标注哪些是真实交付、哪些是技术栈差异。所有数字来自本地工作区实测（32 个站点 / 640 条活动 / 2023-03 至今历史数据）。

## 一、AI 应用生产环境交付（不只是调通 API）

| 要求 | 仓库内的实现 | 怎么验证 |
| --- | --- | --- |
| 完整交付链路 | 采集/建档 → 检索增强 → 生成 → 审批 → 执行 → 回滚，全链路落库可追溯：`optimization_run` / `optimization_audit` / `optimization_ledger` | 控制台「智能体工作台 → 分析与审批」走一遍，或 `POST /cms/agent/runs/scenarios/healthy` |
| 模型不可用时的降级 | `AiModelGateway` 三级：主模型 → 备选模型 → 规则模板（`explanation.source=RULES`）；RAG 侧再降一级到抽取式回答 | 清空 `cms.agent.ai.base-url` 重启，`/cms/ai/rag/answer` 仍返回带引用的答案 |
| 凭据安全 | `channel_credential` 列级加密（`ChannelCredentialController`），密钥文件 `data/local-admin.key`，签名+时间戳+nonce 防重放 | `POST /cms/console/channel-credentials/check` |
| 可观测性 | `agent_execution_trace`（节点耗时/ token）、`AgentTelemetryService`、语义缓存命中统计 | `GET /cms/ai/platform/overview` |

## 二、LLM 文本与多模态推理 + 推理优化实战

| 优化手段 | 实现 | 实测效果 |
| --- | --- | --- |
| 语义缓存（省 token 的大头） | `SemanticCache`：prompt 向量相似度 ≥0.93 直接复用答案，TTL 7 天，累计 `tokens_saved` | 同一问题第二次调用 **2648ms → 39ms**，缓存命中 1 次省 1540 tokens |
| 本地 embedding（省 API 成本） | `LocalEmbedding`：分词+字符三元组哈希到 384 维并 L2 归一化，零依赖零调用 | 3041 个 chunk 建索引 **569ms** |
| 分级模型路由 | `AiModelGateway.resolve()`：主模型/备选模型 + 超时 + 瞬时重试 | 主模型 5xx 时自动切备选，返回 `fallbackUsed=true` |
| 上下文裁剪 | RAG 片段按分数取 top-K，单 chunk ≤520 字符且按句切分，避免无效长上下文 | 回答请求 prompt 稳定在 1.5k tokens 量级 |
| 多模态 | `AiImageGenerator`（OpenAI 兼容图像接口）+ 素材/落地页视觉证据 | `POST /cms/ai/image/create` |
| 成本可计量 | `token_usage` 字段 + 缓存 `tokens_saved` + 评测包含 latency | `GET /cms/ai/platform/overview` 的 `cache` 区块 |

## 三、RAG / 向量库 / Prompt Engineering

| 能力 | 实现位置 | 说明 |
| --- | --- | --- |
| 语料与分块 | `RagService#chunk` | 文章、读者反馈、广告文案入索引；按段落 + 句号切分，520 字符 / 80 重叠，单文档最多 12 chunk |
| 向量存储 | `ai_rag_chunk` 表（H2） | 384 维向量以半精度 Base64 存储，约 1KB/条；内存中常驻 float[] 供检索 |
| 混合检索 | `RagService#search` | `0.72 × 余弦 + 0.28 × 关键词命中率`，避免模糊近邻淹没精确词（型号、法条） |
| 引用溯源 | `RagService.Citation` | 每条回答带 `docType/docId/title/chunkIndex/snippet`，前端可直接定位原文 |
| 检索质量兜底 | 拒绝回答 | 证据不足时明确输出「缺少哪些数据」，评测里作为独立用例 |
| Prompt 注册表 | `PromptRegistry` + `ai_prompt_template` | 每个 prompt 有 key/版本/变量，单一 active 版本；`POST /cms/ai/prompts/activate` 灰度回滚 |
| Prompt 评测 | `AiEvalService` + `ai_eval_run` | 黄金集 8 条（6 条可答 + 2 条应拒答），指标：关键词召回、通过率、延迟、缓存命中率 |

实测（`POST /cms/ai/eval/run`）：**7/8 通过，平均关键词召回 0.94，平均延迟 3.9s**（含真实模型调用，缓存旁路）。

## 四、全栈能力（React / Python / 数据库 / 缓存 / 队列 / 建模）

| JD 要求 | 本项目现状 | 差距说明 |
| --- | --- | --- |
| 前端 | React 18 + TypeScript + Vite，控制台 12 个路由、命令面板、SSE 流式输出 | 组件以模板字符串渲染为主，不是纯函数组件树；Vue 未使用 |
| 后端 | Spring Boot 2.7 / Java 17，分层：controller → service → dao(MyBatis) | 语言是 Java 而非 Python；业务规模与工程规范对齐 |
| 数据库 | H2（本地）/ MySQL（生产 profile），MyBatis + JdbcTemplate，60+ 表 | — |
| 缓存 | `SemanticCache`（LLM 语义缓存）+ 控制台 30s 结果缓存 | 未接 Redis 集群，`RedisTemplate` 依赖已在 pom 中 |
| 异步任务队列 | `AiJobQueue`：DB 队列 + 状态机（QUEUED/RUNNING/SUCCEEDED/DEAD）+ 指数退避 + 幂等键 | 单实例轮询；生产可换 SQS/Celery |
| 数据建模 | 活动/文章/素材/规则/审批/账本/观测 7 个域，含唯一约束与幂等主键 | — |

## 五、AI 编程工具与人机协作工作流

- 本仓库的 AI 层（RAG / Prompt / Cache / Queue / Eval）由 AI 编码工具在人工约束下完成：先定接口与表结构，再逐模块实现、编译、跑评测回归。
- 可复现的验证脚本：`python tools/qa/ai_platform_check.py`（一键跑通检索、回答、缓存、队列、评测并输出报告）。
- 约定：任何 prompt 改动必须过 `ai_eval_run` 才能上线；任何索引/评测任务必须走队列，保证重启可恢复。

## 六、加分项：Martech 行业系统搭建

- 投放侧：TikTok / Meta / Google / Taboola 四个平台的数据模型、账户与凭据管理、规则引擎、预算试算与回滚账本。
- 内容侧：32 个站点、640 条活动、AI 生成的多语言素材与文案、SEO 关键词与页面 RPM 归因。
- 分析侧：漏斗、国家/市场分层、素材衰减、离线回放评估、五智能体编排。
- 行业约束：合规雷达（平台政策/绝对化用语/地区披露）、HSA/FSA、GDPR 相关文案检查。

## 七、如何演示（5 分钟）

```powershell
# 1) 启动（无窗口）
Start-LocalCms.vbs

# 2) 平台能力总览
curl http://127.0.0.1:9901/cms/ai/platform/overview

# 3) 检索 + 引用
curl -X POST http://127.0.0.1:9901/cms/ai/rag/search -H "Content-Type: application/json" ^
     -d "{\"query\":\"折叠跑步机适合小公寓吗\",\"site\":\"cccooperate\",\"topK\":3}"

# 4) 一句话验收（检索/缓存/队列/评测/拒答全跑）
python tools\qa\ai_platform_check.py
```

## 八、已知边界（不包装）

1. 向量检索是 H2 + 内存余弦，适合十万级 chunk；再大需要 pgvector/Milvus，接口已隔离在 `RagService`。
2. embedding 为本地哈希模型，语义能力弱于 bge/text-embedding-3；因此加了中英词表做查询改写，跨语言召回仍不如专业向量模型。
3. 队列是单实例轮询，没有做分区消费与优先级队列。
4. 后端是 Java，若岗位强制 Python，可把 RAG/评测 worker 抽成 FastAPI 服务（接口已按 HTTP 语义设计）。
