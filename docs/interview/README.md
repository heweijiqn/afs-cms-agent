# 面试材料总目录

> 这套材料围绕一个真实项目：**广告投放管理平台与多智能体优化系统**（`D:\广告\afs-cms`）。
> 目标不是「背题库」，而是让你在被追问三层之后还能站得住。

---

## 一、先看这个（60 秒版）

```
docs/interview/
├── README.md              ← 你在这里（导航 + 使用路线 + 验证记录）
├── project-map.md         ← 事实卡（全部实测：测试结果 / 库内数字 / 接口返回）
├── resume-final.md        ← 一页简历成稿（带占位符，改完直接导出 PDF）
├── resume-template.md     ← 简历写什么（3 个版本 + 量化口径 + 反例改写）
├── star-method.md         ← 面试口述（10 个 STAR 故事，90 秒/个）
├── qa-collection.md       ← 66 个问答（A 项目 / B LLM / C 架构 / D 业务 / E Java / F 编码）
├── baguwen.md             ← 八股文（8 章 82 题，每题挂回本项目）
├── coding-challenges.md   ← 手撕代码（10 题，取自项目真实逻辑，代码实跑通过）
├── mock-interview.md      ← 45 分钟逐字稿 + 评分表 + 反问清单
└── checklist.md           ← 投递/面试前 checklist（含「先跑全量测试」）+ 红线清单
```

根目录还有两份：

| 文件 | 定位 |
| --- | --- |
| `简历版说明.md` | 项目**说明书**（简历附件 / 作品集，能直接给人看） |
| `README.md` | 项目**技术说明**（怎么跑、有什么特性） |

---

## 一·五、验证记录（2026-11-25 实测，材料里的数字都来自这里）

| 验证项 | 结果 |
| --- | --- |
| `mvn clean test` | **BUILD SUCCESS**，10 个测试类 / **31 个用例**，0 失败 0 错误 0 跳过 |
| 编译规模 | 主源码 **245** 个文件，测试 **12** 个文件 |
| `mvn package -DskipTests` | 产出 `target/afs-cms.jar` ≈ **194 MB** |
| 冷启动（local-cms，9901） | **8.54 秒**；`GET /cms/console/bootstrap` 返回 `mode=cms, localCms=true` |
| `GET /cms/agent/status`（签名后） | 返回 **8 个工具**、8 节点 Mermaid 拓扑、LangGraph4j 1.5.14 / LangChain4j 1.0.1 |
| `GET /cms/agent/safety` | `COPILOT / killSwitch=false / 500.00 / 20% / 200 次` |
| H2 直连查询 | 表 **63** 张；活动 **640**（256/320/32/32）；报表 27,124 行 / 1,341 天；文章 842 |
| 运行态遥测 | 78 次运行 / 461 次节点执行 / 395 次工具调用 / 1,270,648 token |

> 复跑方式见 `checklist.md` §2.0 与 `project-map.md` §9。
> **改代码后请重跑一遍，并回来更新这张表**——材料一旦和代码对不上，面试就会自相矛盾。

---

## 二、三种时间预算的读法

### 只有 1 小时（马上要面试）
1. `resume-final.md` 的「信息密度说明」自检 —— 5 分钟
2. `project-map.md` 的「可写进简历的数字」和「诚实边界」两节 —— 10 分钟
3. `star-method.md` 的 S1（总述）+ S2（护栏）+ S6（安全） —— 20 分钟
4. `qa-collection.md` 的 ★ 题（约 20 题）—— 25 分钟
5. `mock-interview.md` 的「五个红旗」—— 5 分钟

### 有 1 天
1. 上午：`project-map.md` 全文 + 对着源码点开 6 个入口文件（每文件看 5 分钟）
2. 下午：`star-method.md` 10 个故事，每个**开口讲一遍**（不要默读）
3. 晚上：`qa-collection.md` A/B 两组 + `baguwen.md` 的 LLM 与 Agent 章

### 有 7 天（推荐，也是我自己的练法）
| 天 | 内容 | 产出 |
| --- | --- | --- |
| D1 | 读 `project-map.md` + 逐个打开代码入口 | 能手画出图拓扑 |
| D2 | `resume-template.md`，选定版本，改出自己的简历 | 一页简历定稿 |
| D3 | `star-method.md` S1-S5，录 5 段 90 秒语音 | 听回放，砍掉所有铺垫 |
| D4 | `star-method.md` S6-S10 + `mock-interview.md` 对练 | 全程录音一次 |
| D5 | `qa-collection.md` A/B 组 + `coding-challenges.md` 手写 3 题 | 纸上写出可运行代码 |
| D6 | `baguwen.md` 挑弱的两章补 + `qa-collection.md` C/D/E 组 | 每个弱项写 3 句结论 |
| D7 | 完整模拟面试 + `checklist.md` 全过一遍 | 打分 ≥ 4.0 |

---

## 三、四条使用原则

1. **事实卡优先**：任何数字只认 `project-map.md`。材料之间如果冲突，以事实卡 + 源码为准，
   然后回来改材料（别在面试里现场发挥数字）。
2. **讲代价，不只讲优点**：每个设计都要能说「我放弃了什么」。这是区分「做过」和「看过」的关键。
3. **短板前置**：合成数据、未上线、evidenceRefs 弱约束——自己先说，比被问出来强。
4. **开口练，不要默读**：默读会产生「我懂了」的错觉；录音回放才知道自己哪里啰嗦。

---

## 四、维护约定（改了代码就回来同步）

| 代码变动 | 需要更新的材料 |
| --- | --- |
| 图节点 / 边变化 | `project-map.md` §4.1、`star-method.md` S1、`qa-collection.md` Q17 |
| 工具增删 | `project-map.md` §4.4、`qa-collection.md` Q5、`resume-template.md` 版本 A/B |
| 安全策略默认值变化 | `project-map.md` §5.1、`qa-collection.md` Q6、`mock-interview.md` 第 22 分钟 |
| 检查点实现变化 | `project-map.md` §4.5、`star-method.md` S5、Q33 |
| 表数量变化 | `project-map.md` §2 表格、`简历版说明.md` §3 |
| 测试类增删 | `project-map.md` §7、`resume-template.md` 版本 A 末条 |

> 一句话：**材料是代码的投影**。代码变了材料不更，面试时就会自相矛盾。

---

## 五、这套材料刻意「没写」的东西

- 没有编造的线上故障故事（没上线就没事故）
- 没有虚构的性能指标（没压测就不写 QPS）
- 没有夸大算法（注水分配是启发式实现，不是求解器）
- 没有假装 RAG / 向量库 / K8s（这个项目确实不需要）

> 面试的本质是**降低对方的验证成本**。你给的东西越可验证，对方越愿意信你。
