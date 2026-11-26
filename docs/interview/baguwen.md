# Java 后端 + AI Agent 面试八股文（每条挂回 AFS-CMS）

> 用法：面试前按章节过一遍，**先背标准答，再记「本项目落地」这一句**——面试官真正想听的是「你在真实代码里怎么踩的」。
> 所有数字与文件位置以 [`project-map.md`](./project-map.md) 为准，本文不另造数据。
> 难度标注：★ 基础必答 / ★★ 需要展开 / ★★★ 高频深挖，答不好会被追着打。
> 事实纪律：本项目签名是 **MD5 + salt（历史开放 API 契约，不是 HMAC 最佳实践）**；
> 本项目**没有**向量库 / RAG、没有 Kubernetes、没有分库分表、没有 Kafka、没有 ES、没有 Redis 集群。
> 不要把「业界常见做法」说成「我们做了」。

**索引**：① Java 基础与集合（Q1-Q10）｜② 并发与 JVM（Q11-Q21）｜③ Spring 生态（Q22-Q32）｜④ MyBatis 与数据库（Q33-Q42）
⑤ Redis 与分布式（Q43-Q51）｜⑥ LLM 与 Agent（Q52-Q63）｜⑦ 广告业务算法（Q64-Q72）｜⑧ 工程与安全（Q73-Q82）
⑨ 附录：本项目没用到但要会答的高频题（Kafka / ES / 分库分表 / K8s / 向量库 等）

---

## 一、Java 基础与集合

### Q1. HashMap 的扩容机制讲一下？（★★）
**标准答**：默认容量 16、负载因子 0.75，元素数超过 `容量 × 0.75` 触发扩容，容量翻倍。
扩容时 JDK 8 不再重新计算 hash，而是用 `hash & oldCap` 把原桶拆成「低位链」和「高位链」两条，分别留在原索引 `i` 和 `i + oldCap`，避免 JDK 7 头插导致的死链问题。
链表长度 ≥ 8 且容量 ≥ 64 时转红黑树，树节点 ≤ 6 时退化回链表；容量不足 64 时优先扩容而不是树化。
**本项目落地**：签名验签要求「同样的入参必须算出同样的串」，所以 `controller/filter/AuthInterceptor.java:117-126` 用的是 `TreeMap` 而不是 `HashMap`；工具返回给模型的 JSON 用 `LinkedHashMap`（`AdvisoryTools.java:47`）保证字段顺序稳定，方便排查与回归比对。
**追问**：为什么负载因子是 0.75？——空间与冲突概率的折中，泊松分布下桶内 8 个节点的概率约亿分之六，这也是树化阈值取 8 的依据。
扩容一定线程不安全吗？——是，并发 put 可能丢数据或死循环，并发场景必须 `ConcurrentHashMap`。

### Q2. 为什么 HashMap 容量必须是 2 的幂？hash 扰动有什么用？（★★）
**标准答**：下标用 `(n-1) & hash` 代替取模，只有 n 是 2 的幂时 `n-1` 才是全 1 低位掩码，等价于取模但快得多。
所以 `hash()` 里做 `h ^ (h >>> 16)`，把高 16 位异或到低位，否则低位信息不足会让冲突集中。
`tableSizeFor` 用五次无符号右移把任意 capacity 向上取整到 2 的幂。
**本项目落地**：非重复（nonce）去重依赖的是 `ConcurrentHashMap.putIfAbsent` 的 key 散列质量（`AuthInterceptor.java:185`），key 是 32 位 UUID 字符串，散列均匀，不存在热点桶；我们没有把 nonce 放进 `HashMap` 再自己加锁。
**追问**：初始容量怎么设？——已知元素数 n 时设 `(int)(n/0.75f)+1` 再交给 `tableSizeFor`，避免多次扩容。
为什么不用 `Hashtable`？——全表锁，吞吐差，已被淘汰。

### Q3. String 为什么设计成不可变？字符串常量池是怎么回事？（★）
**标准答**：`final class` + `private final char[]/byte[]`，不可变带来三个收益：hash 可缓存（`hashCode` 只算一次）、可安全共享（常量池复用）、天然线程安全。
字符串拼接在循环里会不断产生新对象，要用 `StringBuilder`；`substring` 在 JDK 7+ 会复制，不会像 JDK 6 那样持有整个原数组。
**本项目落地**：签名串由 `StringBuilder` 逐段 append（`AuthInterceptor.java:147-155`），一次请求只分配一个 builder；对外异常进日志前统一走 `redact()` 做 `String.replace`（`AiModelGateway.java:252-258`），因为不可变所以替换只影响新串、不会污染原始 message。
**追问**：`intern()` 什么时候用？——大量重复的枚举型字符串去重（如 provider 名）；但常量池在元空间，滥用会 OOM。
`String` 为什么适合做 HashMap key？——不可变 + hash 缓存，key 的 hash 不会变。

### Q4. equals 与 hashCode 的契约是什么？违反会怎样？（★★）
**标准答**：`equals` 相等则 `hashCode` 必须相等；`hashCode` 相等 `equals` 不一定相等。
违反后元素放进 `HashMap`/`HashSet` 会出现「存进去取不出来」——因为 put 和 get 定位到不同桶。
只重写 `equals` 不重写 `hashCode`，或者用可变字段参与 `hashCode` 后修改对象，都会踩这个坑。
**本项目落地**：nonce 去重直接用 `String` 的天然契约（`AuthInterceptor.java:185-192`）；`record` 类型（如 `AiPlatformSettings`、`BiddingReport`）由编译器生成 `equals/hashCode`，我们不再手写，减少「改字段忘改 equals」的风险。
**追问**：为什么 `equals` 要用 `Objects.equals`？——防 NPE 且能正确处理 null。
`BigDecimal` 的 equals 有什么坑？——见 Q8。

### Q5. 泛型擦除是什么？带来哪些限制？（★★）
**标准答**：泛型只存在于编译期，运行期被擦成原始类型/Object，编译器在读写处插入 checkcast。
限制：不能 `new T[]`、不能对泛型做 `instanceof`、静态成员不能用类泛型参数、基本类型不能做类型实参、泛型方法重载可能因擦除后签名相同而冲突。
擦除的补偿手段：显式传 `Class<T>`、`TypeReference`/`ParameterizedTypeReference` 保留类型信息。
**本项目落地**：图状态统一用 `StateGraph<OptimizationGraphState>` 在编译期做类型检查，运行期落库仍是 `Map<String,Object>`；反序列化靠 Jackson 的 `TypeReference<Map<String,Object>>`（`JdbcCheckpointSaver.java:27`），这正是擦除后必须显式携带类型信息的典型写法。
**追问**：`List<String>` 和 `List<Integer>` 的 `getClass()` 相等吗？——相等，都是 `ArrayList.class`。
怎么在运行期拿到泛型？——通过父类/字段/方法签名里的 `ParameterizedType`（Spring 的 `ResolvableType` 就是这么干的）。

### Q6. Java 异常体系说一下，checked 和 unchecked 怎么选？（★）
**标准答**：`Throwable` 分 `Error`（不可恢复，如 OOM）与 `Exception`；`Exception` 再分 checked（`IOException`）与 unchecked（`RuntimeException`）。
checked 强制调用方处理，适合「调用方有能力恢复」的场景；业务校验失败、参数非法用 unchecked。
工程原则：不要吞异常、不要 `catch (Exception e) {}` 后什么都不做、异常要带上下文但不能带敏感信息。
**本项目落地**：安全策略拒绝用 `ResponseStatusException` 携带明确语义的状态码——Kill Switch 用 423 LOCKED，模式/幅度/日上限越界用 409 CONFLICT（`AgentSafetyPolicyService.java:111-132`）；工具执行异常不抛栈，转成 `{"available":false,...}` 交给模型（`AdOptimizationGraph.java:572-576`）。
**追问**：为什么工具异常不往外抛？——LLM 拿到结构化错误还能自我修正，抛栈会直接终止整张图。
`ResponseStatusException` 和 `@ControllerAdvice` 的关系？——前者由 Spring 默认异常解析器处理成对应状态码，本项目没有再包一层 Advice（见 Q32）。

### Q7. ArrayList 与 LinkedList 怎么选？数组扩容代价多大？（★）
**标准答**：`ArrayList` 基于连续数组，随机访问 O(1)，尾部追加均摊 O(1)，扩容 1.5 倍并整体复制；`LinkedList` 基于双向链表，头尾操作 O(1)，随机访问 O(n)，每个节点额外两个指针。
实际工程里 99% 用 `ArrayList`，因为 CPU 缓存局部性远胜链表；`LinkedList` 只在「频繁在已知节点处增删 + 不做随机访问」时有意义。
**本项目落地**：图节点输出、trace 列表、工具调用记录全部用 `ArrayList`（如 `AdOptimizationGraph` 的 `nodeTrace`、`toolTrace`），因为只做追加和遍历；跨线程的 emitter 列表用 `CopyOnWriteArrayList`（`AgentTelemetryService.java:25`），读多写极少。
**追问**：`CopyOnWriteArrayList` 适合什么场景？——读远多于写，写时复制整个数组，写开销大但读无锁。
为什么不用 `Vector`？——方法级 synchronized，已被 `CopyOnWriteArrayList`/`Collections.synchronizedList` 取代。

### Q8. 金额为什么必须用 BigDecimal？equals 和 compareTo 有什么区别？（★★）
**标准答**：`double` 是二进制浮点，`0.1+0.2 != 0.3`，金额用 `double` 会累积误差；`BigDecimal` 是十进制精确表示。
必须用 `BigDecimal(String)` 或 `valueOf` 构造，`new BigDecimal(0.1)` 会把 double 的误差原样带进来。
`equals` 比较「值 + scale」，`1.0` 与 `1.00` 不 equals；`compareTo` 只比数值。判等用 `compareTo == 0`。
**本项目落地**：预算、消耗、eCPM 全链路 `BigDecimal`，所有落库前 `setScale(2, RoundingMode.HALF_UP)`（`AgentSafetyPolicyService.java:81-82`、`AdvisoryTools.java:142-145`）；日累计变动用 `SUM(ABS(after_budget - before_budget))` 在 SQL 里算（`:137`），不在 Java 里拼 double。
**追问**：除法怎么除？——必须指定 scale 和 RoundingMode，否则除不尽抛 `ArithmeticException`；本项目 ROAS 用 `divide(..., 4, HALF_UP)`（`AdvisoryTools.java:58-59`）。
`RoundingMode.HALF_UP` 和 `HALF_EVEN` 怎么选？——财务口径常用 HALF_UP，统计口径用 HALF_EVEN 减少系统性偏差。

### Q9. JDK 17 有哪些语言特性你在用？record 适合什么场景？（★）
**标准答**：`record` 用于不可变数据载体，自动生成构造器、访问器、`equals/hashCode/toString`；`sealed` 限制继承体系；`instanceof` 模式匹配省掉强转；`switch` 表达式带箭头语法；文本块 `"""` 写多行 SQL/提示词；`var` 做局部变量类型推断。
`record` 不适合需要可变状态或框架要求无参构造的实体（JPA/MyBatis 映射对象）。
**本项目落地**：`Policy`、`ChatCallResult`、`ResolvedModel`、`TraceRow`、`CheckpointView` 全是 `record`（`AgentSafetyPolicyService.java:30-32`、`AiModelGateway.java:275-280`、`AgentTelemetryService.java:221-224`）；多行 SQL 用文本块（`AgentSafetyPolicyService.java:36-39`），提示词也用文本块（`AdOptimizationGraph.java:677`）。
**追问**：record 能做局部变量吗？——可以，JDK 16+ 支持局部 record，适合临时聚合。
record 的字段能改吗？——不能，字段隐式 final；要可变请用普通类。

### Q10. 浅拷贝、深拷贝与不可变集合？（★★）
**标准答**：浅拷贝只复制引用，内部对象仍共享；深拷贝递归复制。`Object.clone()` 是浅拷贝且绕过构造器，不推荐。
`Collections.unmodifiableList` 是视图，原集合变了它跟着变；`List.copyOf` 会做真正的防御性拷贝且拒绝 null。
对外暴露内部集合时必须拷贝或包装，否则调用方一句 `add` 就能破坏封装与线程安全。
**本项目落地**：`GraphOutcome` 里对 `nodeTrace`、`toolTrace` 做 `List.copyOf` 后再返回（`AdOptimizationGraph.java:602-609`），保证调用方拿到的快照不会因图继续执行而变；工具结果列表也用 `List.copyOf`（`AdOptimizationGraph.java:670-673`）。
**追问**：`List.of` 和 `List.copyOf` 区别？——前者不接受 null 且总是新建，后者传入已是不可变列表时可能直接复用。
不可变对象一定线程安全吗？——只有所有字段都 final 且引用对象也不可变时才是。

---

## 二、并发与 JVM

### Q11. 线程池的七个参数和任务提交流程？（★★）
**标准答**：`corePoolSize`、`maximumPoolSize`、`keepAliveTime`、`unit`、`workQueue`、`threadFactory`、`handler`。
提交流程：当前线程数 < core 就新建核心线程；否则入队；队列满且线程数 < max 才新建非核心线程；再满则执行拒绝策略。
**关键反直觉点**：队列满之前绝不会超过 corePoolSize，所以无界队列等价于 `maximumPoolSize` 失效。
**本项目落地**：`config/AsyncTaskConfig.java:15-24` 显式定义 `asyncTaskExecutor`：core 10、max 30、queue 100、线程名前缀 `asyncTask-config-`；调度线程池独立 20 个线程（`:27-33`），避免定时同步任务和业务异步任务互相饿死。
**追问**：为什么两个池分开？——`@Scheduled` 的渠道数据同步是长任务，和短业务任务混用会拖垮响应。
不初始化会怎样？——`ThreadPoolTaskExecutor` 必须显式 `initialize()`，否则第一次提交才建线程。

### Q12. 拒绝策略怎么选？队列容量设多大？（★★★）
**标准答**：`AbortPolicy` 抛 `RejectedExecutionException`（默认，适合不能丢任务且调用方能处理）、`CallerRunsPolicy` 回退到调用线程（天然背压，但会阻塞 Tomcat 线程）、`DiscardPolicy`/`DiscardOldestPolicy` 静默丢弃（几乎不建议）。
队列容量不是越大越好：大队列会把 OOM 风险从「任务被拒」变成「堆被堆满」，且任务延迟不可控。
**本项目落地**：`AsyncTaskConfig` 没显式设 handler，用的是默认 `AbortPolicy`——这是有意的：同步任务被拒比静默丢一次渠道数据好排查，日志里能看到异常；queue 100 是「允许尖峰、但拒绝无限堆积」的折中。
**追问**：`CallerRunsPolicy` 什么时候合适？——任务可容忍延迟且需要背压时，比如内部批处理；Web 请求线程池用它会打满 Tomcat。
线程池打满怎么排查？——看 `thread pool` 指标、堆栈 dump、队列长度；先定位是任务变慢还是流量变大。

### Q13. 线程数怎么估？CPU 密集和 IO 密集差在哪？（★★）
**标准答**：CPU 密集约 `N+1`（N 为核数），IO 密集约 `N × (1 + 等待时间/计算时间)`。
真实取值要靠压测：先按公式给初值，再用目标 QPS 下的 P99 与队列长度调。
**本项目落地**：我们是 IO 密集（等 LLM、等渠道 HTTP、等 DB），理论上可以给到几十；但 LLM 调用是长尾（单次 20-90 秒，超时默认 90 秒），所以 core 10 / max 30 是「不让并发打爆上游配额」的人为限流——日 LLM 调用上限 200（Q60）也是同一思路的另一道闸。
**追问**：为什么不用虚拟线程？——JDK 17 没有（需 21+），且 LangChain4j 的同步 HttpClient 阻塞在虚拟线程上收益依赖 pinning 情况，换运行时是独立评估项。
核数怎么拿？——`Runtime.getRuntime().availableProcessors()`。

### Q14. ConcurrentHashMap 怎么做到高并发？JDK 7 和 8 有什么区别？（★★★）
**标准答**：JDK 7 是分段锁（Segment 继承 ReentrantLock，默认 16 段）；JDK 8 改为 `CAS + synchronized 锁单个桶头节点`，锁粒度更细，且用 `volatile` 读写 `val`/`next` 保证可见性。
size 用 `baseCount + CounterCell[]` 分散计数，避免全局竞争。
`putIfAbsent`、`computeIfAbsent`、`merge` 是原子的复合操作，但「先 get 再 put」不是。
**本项目落地**：三处关键并发容器——`AdOptimizationGraph.activeSessions`（`:78`）、`AgentTelemetryService.runStreams`（`:25`）、`AuthInterceptor.USED_NONCES`（`:45`）。nonce 防重放必须用 `putIfAbsent`（`:185`）而不是先 containsKey 再 put，否则并发下两个相同 nonce 都能通过。
**追问**：`computeIfAbsent` 里能改同一个 map 吗？——不能，会死循环或抛异常，这是官方明确禁止的递归更新。
为什么 value 不允许 null？——无法区分「不存在」和「值为 null」，所以 nonce 的过期时间用 `Long` 而不是可空类型。

### Q15. volatile 保证什么？不保证什么？（★★）
**标准答**：保证可见性（写立即刷主存、读强制从主存读）和禁止指令重排（插入内存屏障）；**不保证原子性**（`i++` 依然不安全）。
典型正确用法：状态标志位、双重检查锁的单例引用、一次性发布的配置快照。
**本项目落地**：`agent/CmsCampaignDataService.java:125-126` 用两个 `volatile` 字段缓存「已加载的数据日期 + 加载时间戳」，读多写少、允许短暂不一致，用 volatile 就够，不必上锁。
**追问**：双重检查锁为什么必须 volatile？——防止「分配内存→赋值引用→初始化」重排导致别的线程拿到半初始化对象。
volatile 和 `AtomicInteger` 区别？——后者用 CAS 保证原子性，代价是可能的自旋。

### Q16. synchronized 的原理与锁升级？（★★）
**标准答**：修饰代码块用 `monitorenter/monitorexit`，修饰方法用 ACC_SYNCHRONIZED 标志；锁信息在对象头 Mark Word。
升级路径：无锁 → 偏向锁 → 轻量级锁（CAS 自旋）→ 重量级锁（OS 互斥量，线程阻塞）；JDK 15 起偏向锁默认关闭并在后续版本废弃。
**本项目落地**：`ChannelCredentialController.loadKey()` 用 `synchronized`（`:411-433`）——因为它要「文件不存在就生成」，不加锁两个线程会各生成一把密钥、后写的覆盖先写的，历史密文就永远解不开；这是「初始化一次」而不是「高并发热点」，方法级锁完全够用。
**追问**：为什么不用 `ReentrantLock`？——这里不需要超时、中断、公平锁等高级能力，synchronized 更简单且不会忘记 unlock。
锁粒度怎么定？——只包住临界区，不要在锁里做 IO/HTTP。

### Q17. ThreadLocal 的原理和内存泄漏？（★★★）
**标准答**：每个 `Thread` 持有 `ThreadLocalMap`，key 是 `ThreadLocal` 的弱引用，value 是强引用。
线程池场景下线程长期存活，key 被回收后 value 仍被 Entry 强引用 → 泄漏，所以**必须 try/finally 里 remove**。
`InheritableThreadLocal` 能传给子线程，但线程池复用线程时传递会串数据，要用 `TransmittableThreadLocal`。
**本项目落地**：`controller/filter/KiwiContext.java:8` 用 `ThreadLocal<Context>` 存当前登录用户，`AuthInterceptor.afterCompletion` 里 `KiwiContext.clear()`（`:88-91`）——这行不是可选的：Tomcat 线程复用，不清就会让下一个请求读到上一个用户，属于越权级别的 bug。
**追问**：`remove` 和 `set(null)` 区别？——`set(null)` 留下一个 key 弱引用已死的 Entry，仍占内存；`remove` 才真正清掉。
异步任务里怎么拿用户？——本项目没有跨线程传用户，异步任务只做渠道同步，不依赖登录态。

### Q18. JMM 与 happens-before 规则？（★★★）
**标准答**：JMM 定义了线程与主内存的抽象关系，解决可见性、原子性、有序性。
happens-before 八条主要规则：程序顺序、监视器锁（unlock 先于后续 lock）、volatile（写先于读）、线程启动（start 先于线程内动作）、线程终止、中断、对象终结、传递性。
**本项目落地**：`USED_NONCES` 的「先 putIfAbsent 再判过期」依赖 CHM 内部的 volatile 语义保证可见性（`AuthInterceptor.java:180-194`）；图状态在节点间传递靠 LangGraph4j 的单线程顺序执行，不自造共享可变状态——这是刻意的设计约束，能不加锁就不加锁。
**追问**：`final` 字段的语义？——正确构造后，其他线程能看到 final 字段的初始化值（这也是不可变对象安全发布的基础）。
`as-if-serial` 是什么？——单线程内重排不能改变程序结果，是编译器优化的边界。

### Q19. CAS 与 ABA 问题？（★★）
**标准答**：CAS 包含「内存值、期望值、新值」，靠 CPU 的 `cmpxchg` 指令保证原子性，失败返回 false 由调用方自旋。
ABA：值从 A 改成 B 又改回 A，CAS 察觉不到中间变化。解决用版本号（`AtomicStampedReference`）或时间戳。
CAS 的代价：高竞争下自旋空转吃 CPU；只能保证单个变量的原子性。
**本项目落地**：`putIfAbsent` 内部就是 CAS，用于 nonce 一次性判定（`:185`）；预算累加这类「读-改-写」我们没在 Java 里做，而是下沉到 SQL 的 `SUM(ABS(...))`（`:136-142`），用数据库的一致性替代应用层 CAS。
**追问**：`LongAdder` 和 `AtomicLong` 怎么选？——高并发计数用 `LongAdder`（分散 cell），需要精确读且并发不高用 `AtomicLong`。
`AtomicInteger` 能保证 `i++` 吗？——能，`incrementAndGet` 是 CAS 循环。

### Q20. JVM 内存结构、GC 分代与 G1？（★★）
**标准答**：堆（新生代 Eden + 两个 Survivor，老年代）、元空间（类元数据，本地内存）、虚拟机栈、本地方法栈、程序计数器、直接内存。
分代假设：绝大多数对象朝生夕死。新生代用复制算法（Minor GC），老年代用标记-清除/整理（Major/Full GC）。
JDK 9+ 默认 G1：把堆切成 Region，按「回收收益」优先回收（Garbage First），可设 `-XX:MaxGCPauseMillis` 做停顿目标，用 SATB 处理并发标记。
**本项目落地**：JDK 17 默认就是 G1，我们没有定制 JVM 参数——因为这个是单机后台系统、QPS 不高，真正的内存风险不在 GC 而在「把整张报表塞进 prompt」：所以工具输出硬截断 6500 字符（`AdOptimizationGraph.java:67,559-577`）、日明细只取 40 天（`AdvisoryTools.java:18,53`）。
**追问**：OOM 怎么定位？——`-XX:+HeapDumpOnOutOfMemoryError` 拿 dump，MAT 看支配树；本项目更常见的是「上下文超长」而不是堆 OOM。
`System.gc()` 能调吗？——不要，只是建议，G1 下可能触发 Full GC。

### Q21. 类加载过程与双亲委派？Spring Boot 为什么能打 fat jar？（★★）
**标准答**：加载 → 验证 → 准备 → 解析 → 初始化。类加载器层次：Bootstrap → Ext/Platform → App → 自定义，加载时先委派父加载器，父加载不到才自己加载。
双亲委派保证核心类不被篡改（自己写个 `java.lang.String` 不会被加载）。
**本项目落地**：Spring Boot 的 `LaunchedURLClassLoader` 打破了「父优先」的严格模型来加载 `BOOT-INF/classes` 与 `BOOT-INF/lib` 里的嵌套 jar；项目里另一个「加载边界」是 `@ComponentScan` 与 `@Profile`：`CmsRuntimeConfiguration` 只扫 `config/controller/dao/service/task/utils`（`:16-19`），`agent` 包在 `agent-sim` 下由 `AgentSimulationConfiguration` 接管——这就是为什么本地 CMS 模式不会把智能体控制器全塞进来。
**追问**：`Class.forName` 和 `ClassLoader.loadClass` 区别？——前者会触发初始化（执行静态块），后者只到加载/链接。
什么场景需要自定义类加载器？——插件化、热部署、多版本隔离（本项目没做）。

---

## 三、Spring 生态

### Q22. IoC 和 DI 到底解决了什么？（★）
**标准答**：控制反转是把「对象的创建与依赖装配」交给容器，依赖注入是它的实现手段。
收益：解耦（面向接口）、可测试（能注入 mock）、统一生命周期管理（单例复用、销毁回调）、横切能力可插拔（AOP）。
**本项目落地**：`AdOptimizationGraph` 不自己 new 网关、检查点、遥测，全部构造器注入（`AdOptimizationGraph.java:80-93`）；测试里因此可以塞确定性桩网关，断言「状态机流转 + 护栏结果 + 落库副作用」，而不是断言模型文案（这是 10 个测试类能稳定跑的原因）。
**追问**：DI 一定需要注解吗？——不需要，XML、`@Bean` 方法、甚至手写工厂都能实现。
单例 Bean 有状态怎么办？——要么改成无状态，要么用 `ThreadLocal`/参数传递（本项目工具类 `AdvisoryTools` 每次 run 都 new 一个，天然隔离）。

### Q23. Bean 的生命周期？（★★）
**标准答**：实例化 → 属性填充 → `Aware` 回调（BeanName/BeanFactory/ApplicationContext）→ `BeanPostProcessor.postProcessBeforeInitialization` → `@PostConstruct` / `InitializingBean.afterPropertiesSet` / `init-method` → `postProcessAfterInitialization`（AOP 代理在这里生成）→ 使用 → `@PreDestroy` / `DisposableBean.destroy`。
**本项目落地**：`AiModelGateway` 的构造器里做参数校验——端口必须在 0-65535、超时必须在 5-600 秒（`:51-56`），配错直接启动失败，而不是等到第一次调用模型才炸；`AdOptimizationGraph` 构造器里就 `buildGraph()` 编译图（`:89`），图有问题启动即暴露。
**追问**：`@PostConstruct` 和构造器区别？——构造器执行时依赖可能还没注入完，`@PostConstruct` 在所有依赖就绪后调用。
原型 Bean 谁负责销毁？——容器不管，调用方自己负责。

### Q24. 构造器注入、setter 注入、字段注入怎么选？（★★）
**标准答**：构造器注入是首选：依赖不可变（final）、对象创建即完整、循环依赖会直接启动失败而不是静默成功、单测不用容器。
字段注入（`@Autowired` 直接标字段）最省事但最差：无法 final、不能脱离容器 new、隐藏依赖过多。
**本项目落地**：agent 包几乎全构造器注入；`AuthInterceptor` 用了字段注入 `@Autowired UserMapper`（`:47-48`），这是历史遗留写法、不是推荐范式——面试时可以直接说「新代码我会用构造器注入，这个类应该重构」。
**追问**：构造器注入怎么解决可选依赖？——`ObjectProvider<T>` 或 `@Nullable` 参数。
循环依赖用 setter 注入能解决吗？——能（配合三级缓存），但那是把设计问题掩盖了。

### Q25. Spring 三级缓存怎么解决循环依赖？为什么需要三级？（★★★）
**标准答**：一级 `singletonObjects` 放成品；二级 `earlySingletonObjects` 放提前暴露的半成品；三级 `singletonFactories` 放能产出代理的 `ObjectFactory`。
A 依赖 B、B 依赖 A：A 实例化后把工厂放进三级缓存 → 填充属性时去创建 B → B 回头 get A，从三级缓存拿到工厂生成（可能是代理的）A 放入二级 → B 完成 → A 完成。
第三级存在的唯一理由是「AOP 代理」：如果只用二级缓存直接放原始对象，B 拿到的是没被代理的 A，注入的就是错误引用。
**本项目落地**：本项目没有循环依赖（构造器注入 + 分层清晰：controller → service/agent → dao）；`StaffSecurity` 与 `SecurityFilterChain` 是同包 `@Bean` 方法参数注入，天然无环。
**追问**：哪些循环依赖解决不了？——构造器注入的环、prototype 作用域的环、`@Async` 代理的环。
`@Lazy` 能解决吗？——能打断，但只是把问题延后到首次调用，属于止痛药。

### Q26. AOP 的实现原理？为什么同类自调用切面失效？（★★）
**标准答**：Spring AOP 用运行期代理：目标类有接口默认 JDK 动态代理，否则 CGLIB 生成子类。
调用方持有的是代理对象，代理在方法前后织入增强；`this.method()` 走的是原始对象，不经过代理，所以切面不生效。
解决：注入自身代理（`AopContext.currentProxy()`）、拆到另一个 Bean、或用 AspectJ 编译期织入。
**本项目落地**：本项目**没有**自定义 `@Aspect`（全库搜不到 `@Aspect`/`@Around`），横切能力用的是 Spring 自带的 `@Transactional`（26 处）和 Security filter chain；但正因为 `@Transactional` 就是 AOP，Q27 的失效场景全部成立。
**追问**：CGLIB 能代理 final 方法吗？——不能，final 方法不会被增强。
代理对象和原对象的 `getClass()` 一样吗？——JDK 代理不一样，CGLIB 是子类。

### Q27. @Transactional 在哪些情况下会失效？（★★★）
**标准答**：常见失效：①同类自调用（不走代理）；②方法不是 public（CGLIB 可代理 protected，但 Spring 默认只对 public 生效）；③异常被自己 catch 掉；④抛的是 checked 异常而没配 `rollbackFor`；⑤类没被 Spring 管理（自己 new 的）；⑥数据库引擎不支持事务（MyISAM）；⑦多数据源/跨库不在同一事务管理器。
**本项目落地**：迁移脚本上显式写 `@Transactional(rollbackFor = Exception.class)`（`RealisticPlatformDataMigration.java:55`、`RealisticReportConfigs.java:42`）——因为它们会抛 checked 的 `Exception`，不配 `rollbackFor` 默认只回滚 `RuntimeException`，半截数据会留在库里。
**追问**：事务里做 HTTP 调用有什么问题？——占着连接与锁、超时会把事务拖长甚至触发连接池耗尽；本项目 LLM 调用**不在**事务方法里，只在落库的审批/记账方法上开事务。
`@Transactional` 加在接口上有效吗？——JDK 代理下有效，CGLIB 下不推荐，统一加实现类。

### Q28. 七种事务传播行为？（★★）
**标准答**：`REQUIRED`（默认，有则加入无则新建）、`REQUIRES_NEW`（挂起外层、独立新事务）、`SUPPORTS`、`NOT_SUPPORTED`、`MANDATORY`、`NEVER`、`NESTED`（保存点，可部分回滚）。
最常用的是前两个：日志/审计要独立提交用 `REQUIRES_NEW`，否则外层回滚会把日志一起滚掉。
**本项目落地**：全部用默认 `REQUIRED`——审批落库、ledger 记账、策略更新是同一业务动作，要么都成要么都败；没有「即使主流程失败也要留痕」的强需求（审计靠 `agent_execution_trace` 独立 INSERT，本来就绕开业务事务）。
**追问**：`REQUIRES_NEW` 会死锁吗？——会，外层持有行锁、内层又去更新同一行，注意锁顺序。
`NESTED` 和 `REQUIRES_NEW` 区别？——前者共享外层事务、靠 savepoint 回滚，后者是真正独立的事务。

### Q29. Spring Boot 自动装配原理？（★★）
**标准答**：`@SpringBootApplication` 包含 `@EnableAutoConfiguration`，它通过 `AutoConfigurationImportSelector` 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（2.7 之前是 `spring.factories`），按 `@ConditionalOnClass/@ConditionalOnMissingBean` 等条件筛选后导入。
最后 `ConfigurationClassPostProcessor` 解析这些配置类并注册 Bean 定义；用户自定义 Bean 因 `@ConditionalOnMissingBean` 优先。
**本项目落地**：`application-agent-sim.yml:10-11` 显式 `exclude` 了 `RedisAutoConfiguration` 与 `RedisRepositoriesAutoConfiguration`——因为模拟模式要「零外部依赖可跑」，自动装配在没有 Redis 的环境里仍会尝试建连接工厂；这是「自动装配是便利不是魔法，该关就关」的实例。
**追问**：怎么排查自动装配没生效？——`--debug` 打印 condition evaluation report，或 `/actuator/conditions`。
`@ConditionalOnMissingBean` 为什么常用于 starter？——让使用者能用自己的 Bean 覆盖默认实现。

### Q30. @ConditionalOnProperty 和 @Profile 有什么区别？各用在哪？（★★）
**标准答**：`@Profile` 按 `spring.profiles.active` 激活，语义是「环境」；`@ConditionalOnProperty` 按配置项的值激活，语义是「开关」，粒度更细、可不依赖 profile。
两者都是条件装配，都可与 `@Bean`/`@Configuration` 组合。
**本项目落地**：`StaffSecurity` 用 `@ConditionalOnProperty(cms.agent.require-staff-login=true)`（`:19-21`）——双人审批是可选能力，本地开发不该被迫设两个 12 位密码；`RealisticReportConfigs` 用 `afs.workspace.realistic-data`（`:27-29`），种子数据在测试环境默认关闭，避免污染内存库。环境切分才用 `@Profile`（`CmsRuntimeConfiguration` 的 `!agent-sim`、`AfsConfig` 的 `!local-cms`）。
**追问**：开关和 profile 冲突时谁生效？——条件叠加，两个都必须满足。
配置项改了要重启吗？——`@ConditionalOnProperty` 是启动期决策，改值必须重启。

### Q31. Filter、Interceptor、AOP 的执行顺序？（★★★）
**标准答**：请求进来先过 Filter（Servlet 规范，在 DispatcherServlet 之前）→ 再进 HandlerInterceptor 的 `preHandle` → 再进 Controller → 出站时依次 `postHandle`、`afterCompletion`，AOP 环绕在「被代理的方法」上，也就是 Controller/Service 方法内部。
一句话：Filter 包 Interceptor，Interceptor 包 AOP 代理方法，AOP 包目标方法代码。
**本项目落地**：这条顺序在本项目里是硬的——`SecurityHeadersFilter` 是 `@Order(HIGHEST_PRECEDENCE + 10)`（`:16`），`CmsJsonBodyFilter` 是 `+20`（`:27`），`AuthInterceptor` 在 `AfsConfig` 里注册为 `.order(1)`（`:34-36`）。必须先缓存 body（`CmsJsonBodyFilter` 把请求包成 `CachedBodyRequest`），验签才能算 body 的 MD5；如果顺序反了，验签时流已被读空，body hash 永远对不上。
**追问**：Filter 里能拿到 `@Autowired` 的 Bean 吗？——能，`@Component` 的 Filter 也是 Spring Bean。
拦截器里抛异常谁处理？——`preHandle` 抛异常走 ExceptionResolver；本项目鉴权失败直接写 JSON 并 `return false`，不进 Controller。

### Q32. 全局异常处理怎么做？本项目为什么没用 @ControllerAdvice？（★）
**标准答**：`@RestControllerAdvice` + `@ExceptionHandler` 集中处理异常，配合 `@ResponseStatus`/`ResponseEntity` 控制状态码，避免每个 Controller 重复 try-catch。
要点：区分业务异常与系统异常、不要把堆栈和内部细节返回给前端、要打日志且带 traceId。
**本项目落地**：本项目没有 `@ControllerAdvice`：CMS 侧的鉴权失败在 `AuthInterceptor.onFailureLogin` 里手写 JSON（`status` + `msg` + `ResponseUtils.error`，`:93-98`），agent 侧用 `ResponseStatusException` 交给 Spring 默认解析（423/409/400）。诚实评价：这是两套风格并存的历史现状，统一到 `@RestControllerAdvice` 是明确的改进项。
**追问**：`@ExceptionHandler` 能处理 404 吗？——默认不能（404 在进 Controller 前就抛了），需要 `ErrorController` 或 `NoHandlerFoundException` 配置。
异常里带敏感信息怎么防？——见 Q75 的 `redact()`。

---

## 四、MyBatis 与数据库

### Q33. MyBatis 的执行流程？Mapper 接口没有实现类怎么工作的？（★★）
**标准答**：`SqlSessionFactory` 解析配置与 Mapper → `SqlSession` 拿到 `MapperProxy`（JDK 动态代理）→ 代理根据「接口全限定名 + 方法名」找到 `MappedStatement` → `Executor` 走一级缓存/二级缓存 → `StatementHandler` 拼 SQL、`ParameterHandler` 设参 → `ResultSetHandler` 映射结果。
所以 Mapper 接口不需要实现类，方法签名就是 SQL 的 key。
**本项目落地**：项目里 42 个 Mapper（`dao` 32 + `publicapi` 10）全是注解式（`@Select`/`@Insert`，用 `<script>` 拼动态 SQL），没有 XML——好处是 SQL 跟方法贴身、改起来不跨文件；坏处是长 SQL 难读，所以复杂统计（如 `AdsenseDataMapper.java:82-126` 那段 20 多个 `sum()`）在注解里用字符串拼接，可读性确实一般，属于取舍。
**追问**：`#{}` 的占位符最终是什么？——JDBC 的 `?`，由 `PreparedStatement` 设参。
Mapper 方法能重载吗？——不能，MyBatis 用「接口名.方法名」做唯一 key。

### Q34. MyBatis 一级缓存和二级缓存？（★★）
**标准答**：一级缓存是 `SqlSession` 级（默认开启，`SESSION` 作用域），同一 session 内相同 SQL + 参数命中缓存；执行 update/insert/delete 或 commit 会清空。二级缓存是 `Mapper` 级（`<cache/>` 或 `@CacheNamespace`，默认关闭），跨 session 共享，要求实体可序列化，多表关联时容易读到脏数据。
**本项目落地**：本项目**没开二级缓存**。原因很实在：报表数据是「准实时」的，只要渠道同步一跑，缓存就必须失效，而跨 Mapper 的失效很难做对；我们改成在服务层用「数据日期」做进程内缓存（`CmsCampaignDataService.java:125-126` 的 volatile 字段 + 时间戳），失效点单一、可控。
**追问**：一级缓存什么时候会坑人？——同一 session 里两次查询之间被别的 session 改了数据，第二次仍读旧值；Spring 下通常一个事务一个 session。
二级缓存适合什么？——极少变动的字典表/配置表。

### Q35. #{} 和 ${} 的区别？什么时候必须用 ${}？（★★★）
**标准答**：`#{}` 是预编译占位符，由 `PreparedStatement` 设参，天然防注入、能复用执行计划；`${}` 是字符串直接拼接，SQL 注入的唯一入口。
必须用 `${}` 的只有「SQL 结构本身」：表名、列名、排序方向、`LIMIT` 后的关键字等，因为占位符不能当标识符用。用了就必须白名单。
**本项目落地**：`AdResourceMapper.java:94` 的 `order by ${orderParam} ${orderDirection}` 是唯一的 `${}`；安全靠 `AdResourceController.getOrderParam()` 把 4 个逻辑排序键映射成固定列名、未命中一律 `id`（`:100-112`），方向只允许 `asc/desc`、默认 `desc`（`:115-123`）。这是「用 `${}` 但把取值范围锁死」的标准做法；如果直接把请求参数透传，就是 SQL 注入。
**追问**：`${}` 能做参数化吗？——不能，只能靠白名单或 `ESAPI` 之类转义。
`like` 查询怎么防注入？——用 `concat('%', #{kw}, '%')`（本项目全篇这么写，如 `AdResourceMapper.java:70`），不要 `'%${kw}%'`。

### Q36. 分页怎么实现？深翻页有什么问题？（★★）
**标准答**：物理分页用 `LIMIT offset, size`（MySQL）或 `ROW_NUMBER()`（标准 SQL）；`PageHelper` 是拦截器改写 SQL 的便捷方案。
深翻页 `LIMIT 1000000, 20` 要扫描并丢弃 100 万行，越翻越慢；优化用「游标/键集分页」——`where id > lastId order by id limit 20`，或延迟关联（先用覆盖索引拿主键再回表）。
**本项目落地**：手写 `limit #{offset}, #{size}`，没引 `PageHelper`（依赖越少越好，且 H2/MySQL 都要跑）；同时 `page.size = Math.min(100, Math.max(1, pageSize))`（`AdResourceController.java:75`）——限制单页最大 100 条，防止「pageSize=100000」把内存打爆，这是最便宜的一道防护。
**追问**：`count` 查询很慢怎么办？——缓存总数、或用近似值、或前端改成「加载更多」不给总数。
一页里还要查关联表怎么避免 N+1？——见 Q37。

### Q37. N+1 查询怎么发现和解决？（★★）
**标准答**：N+1 是「查 1 次列表 + 每条记录再查 1 次关联」的模式，表现为接口 RT 随列表长度线性增长、日志里同一 SQL 出现 N 次。
解决：`IN` 批量查再内存拼接、`JOIN` 一次取回、MyBatis 的 `<collection>` 嵌套查询配 `fetchType="lazy"`、或干脆加缓存。
**本项目落地**：报表过滤用 `custom_channel_id in (<foreach ...>#{e}</foreach>)`（`AdsenseDataMapper.java:112`）一次查完再分组，而不是循环单个渠道；智能体工具层同理：`dailyReport` 一次返回逐日 + 受众分组 + 素材分组（`AdvisoryTools.java:37-72`），避免模型为了同一份数据来回调 3 次工具——工具调用次数也是钱（Q60）。
**追问**：怎么在测试里防 N+1？——用 `p6spy`/MyBatis 日志断言 SQL 条数，或 H2 里统计执行次数。
懒加载在序列化时为什么会炸？——session 已关闭，抛 `LazyInitializationException`。

### Q38. 索引原理与最左前缀？（★★★）
**标准答**：InnoDB 用 B+ 树，非叶子只存键、叶子存数据且用双向链表相连，所以范围查询和排序友好；聚簇索引（主键）叶子即行数据，二级索引叶子存主键值（需要回表）。
联合索引 `(a,b,c)` 遵循最左前缀：`a`、`a,b`、`a,b,c` 能用；`b`、`b,c` 不能。范围查询后续列失效（`a=? and b>? and c=?` 中 c 用不上）。
索引失效常见原因：列上做函数/表达式、隐式类型转换、`like '%x'` 前导通配、`or` 混用非索引列、区分度太低。
**本项目落地**：本地 `schema.sql` 只建了 3 个唯一索引（`article_info_article_id`、`category(site,value)`、`app_info_app_id`，`:447-449`），因为 H2 里数据量是 32 站点/640 活动、跑得动；生产 MySQL 的 10 个迁移脚本才承载真实索引。诚实的优化方向：`adsense_data` 的所有报表查询都是 `custom_channel_id + date` 组合过滤，应该建 `(custom_channel_id, date)` 联合索引，而不是两个单列索引。
**追问**：为什么联合索引要把区分度高的放左边？——不是硬规则，等值查询下顺序影响不大；真正决定的是「能不能覆盖最左前缀」。
`explain` 里 `type=ALL` 说明什么？——全表扫描，需要加索引或改查询。

### Q39. 慢查询怎么定位和优化？（★★★）
**标准答**：定位：开 `slow_query_log`（阈值如 1s）→ 找出 TOP SQL → `explain` 看 `type/key/rows/Extra`。
优化手段：加合适索引、避免函数包裹列、改写 `or` 为 `union`、分页改游标、大事务拆小、读写分离、必要时归档历史数据。
原则：先看「有没有走索引」，再看「扫描行数」，最后才是「加机器」。
**本项目落地**：这里有一个**自己踩的坑**——`AgentSafetyPolicyService.java:141` 和 `:155` 用 `CAST(created_at AS DATE) = CURRENT_DATE` 统计「今日已提交变动」和「今日 LLM 调用数」。列被函数包裹，索引（如果建了）一定失效，是全表扫描。修法很明确：改成 `created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + 1` 的范围查询。现在能忍是因为库小，但这是面试时能主动讲出来的「我知道它慢、也知道怎么改」。
**追问**：为什么范围查询能走索引？——B+ 树叶子有序，`>=`/`<` 是区间定位。
`count(*)` 和 `count(1)` 有区别吗？——InnoDB 下基本等价，`count(列)` 会跳过 null。

### Q40. 同一套代码怎么同时跑 H2 和 MySQL？（★★）
**标准答**：靠方言兼容 + 把差异收敛到少数地方：用两边都支持的 SQL 子集、必要时开数据库兼容模式、时间/分页/upsert 各自封装。
**本项目落地**：`application-local-cms.yml:12` 的 H2 URL 是 `jdbc:h2:file:./data/cms-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,USER`——`MODE=MySQL` 让反引号和 `limit #{offset}, #{size}` 这种 MySQL 分页语法可用，`DATABASE_TO_LOWER` 统一大小写，`NON_KEYWORDS=VALUE,USER` 把 `VALUE`/`USER` 从保留字里摘出去（否则 `value`、`user` 这两列建表就报错）。
**追问**：H2 兼容模式是万能的吗？——不是，`on duplicate key update`、窗口函数、JSON 函数都有差异；本项目 `AdsenseDataMapper.java:25` 用了 `on duplicate key update`，靠的就是 MySQL 模式。
怎么保证两边行为一致？——CI 里跑两套 profile 的测试（本项目 `test-afs` 用内存 H2，`dev/prod` 指 MySQL）。

### Q41. 幂等写入怎么做？MERGE / upsert 有什么区别？（★★）
**标准答**：幂等的本质是「同一请求执行 N 次，结果与执行 1 次相同」。手段：唯一索引 + `insert ignore`、`on duplicate key update`、`MERGE`、乐观锁版本号、业务去重表。
`MERGE ... KEY(...)` 是「有则更新、无则插入」；MySQL 的 `INSERT ... ON DUPLICATE KEY UPDATE` 依赖唯一键；两者都要注意「并发下可能都判不存在」，最终仍要靠唯一约束兜底。
**本项目落地**：检查点写入用 H2 的 `MERGE INTO agent_checkpoint ... KEY (thread_id, checkpoint_id)`（`JdbcCheckpointSaver.java:92-96`），所以图重放同一个 checkpoint 不会写重；种子数据迁移用「先按标识清理再插入」（`README.md` 本地工作区一节），重复跑不会越跑越多。
**追问**：`MERGE` 在 MySQL 里能用吗？——MySQL 不支持标准 `MERGE`，要用 `ON DUPLICATE KEY UPDATE`；这也是本项目把检查点表放 H2/兼容模式的原因之一。
幂等键怎么选？——业务唯一键（如 `thread_id + checkpoint_id`），不要用自增 ID。

### Q42. 迁移脚本怎么保证可重复执行？（★★）
**标准答**：三件事：建表用 `IF NOT EXISTS`、数据用「幂等 upsert / 先删后插」、加版本记录（Flyway/Liquibase）避免重复执行。
更重要的是**边界**：迁移只能碰自己的种子数据，不能覆盖人工数据。
**本项目落地**：`RealisticPlatformDataMigration` + `RealisticWorkspaceExpansion` 只改 `article_id LIKE 'afs-article-%' AND partner = 'AFS Media'` 的语料（`README.md` 本地工作区「保留人工数据」一条），人工新建的活动/文章/规则一律不动；`RealisticReportConfigs` 按渠道补 `report_data_config`，缺一行补一行、已有不动（`:42` 起）——这就是「幂等 + 边界」两个词的具体实现。
**追问**：为什么不用 Flyway？——本项目 `sql/` 下 10 个脚本是手工按版本管理的，引入 Flyway 是待办；但本地 H2 的 `schema.sql` 用 `IF NOT EXISTS` 已经做到了「每次启动都能跑」。
迁移失败怎么回滚？——`@Transactional(rollbackFor=Exception.class)` + 备份（`data/` 目录已 gitignore）。

---

## 五、Redis 与分布式

### Q43. 缓存穿透、击穿、雪崩分别是什么？怎么防？（★★）
**标准答**：穿透是查不存在的数据，绕过缓存直打 DB；击穿是热点 key 过期瞬间大量请求打 DB；雪崩是大量 key 同时过期或缓存整体挂掉。
防穿透：空值缓存 + 布隆过滤器 + 参数校验；防击穿：互斥锁重建 / 逻辑过期；防雪崩：过期时间加随机抖动 + 多级缓存 + 熔断降级。
**本项目落地**：本项目**没有 Redis**（`application-agent-sim.yml:10-11` 明确 exclude 了 Redis 自动装配），所以严格说这三种问题不适用；但有同构风险：智能体一次分析会连续调 `dailyReport` 等工具，如果每次都全量查库就是「自己打自己」，所以 `CmsCampaignDataService` 用进程内 volatile 缓存数据日期（`:125-126`）。
**追问**：布隆过滤器会误判吗？——只会「假阳性」（说不存在一定不存在，说存在可能不存在）。
逻辑过期怎么实现？——value 里带过期时间，读到过期就异步重建、先返回旧值。

### Q44. 缓存与数据库的一致性怎么保证？（★★）
**标准答**：主流是 `Cache Aside`：读时未命中查库回填，写时「先更库、再删缓存」。
为什么是删而不是更新？——并发写时更新缓存可能写入旧值；删除让下次读自然回填，配合延迟双删降低不一致窗口。
强一致只能靠分布式锁或订阅 binlog（Canal）异步补偿，都要接受最终一致。
**本项目落地**：我们没有「缓存 vs DB」的一致性问题，因为只有一层进程内缓存且以「数据日期」为失效键：日期变了就重查（`CmsCampaignDataService.java:125-126`）。这是刻意选的简单方案——单机场景下引入 Redis 只会多一个需要保证一致性的组件。
**追问**：先删缓存再更库行不行？——不行，删除后、更新前有读请求会把旧值回填。
延迟双删的延迟怎么定？——大于一次主从同步 + 一次读请求耗时，经验值几百毫秒。

### Q45. 分布式锁要满足哪几个条件？怎么实现？（★★★）
**标准答**：四个条件：互斥、不死锁（有超时）、只有持有者能释放（value 存唯一标识，用 Lua 比对后删）、可重入（可选）。
Redis 实现：`SET key val NX PX 30000` 加锁，Lua 脚本释放；Redisson 提供看门狗续期。ZooKeeper 实现靠临时顺序节点，一致性更强但性能低。
**本项目落地**：`service/DistributeLockService.java` 名字叫分布式锁，实现却是**数据库行锁**——`insertIgnore(lockKey)` 保证唯一、`tryLock(key, val, expireAt)` 带过期时间、`tryUnLock` 比对 val 后释放（`:16-29`）。为什么这样：项目要「零外部依赖可跑」，而锁的竞争强度很低（定时任务级别的互斥），DB 行锁完全够。坑也说清楚：类里日志文案还残留 `Failed to unlock redis lock`（`:26`），是历史遗留；真要上 Redis，应该换成 `SET NX PX` + Lua，因为 DB 锁在事务里会长时间占用行锁。
**追问**：锁超时了业务还没跑完怎么办？——看门狗续期（Redisson），或把超时设得远大于 P99。
RedLock 可靠吗？——有争议（Martin Kleppmann 的质疑），对强一致场景更推荐 ZK/etcd。

### Q46. 幂等和去重怎么做？（★★★）
**标准答**：三层次：①唯一约束（数据库兜底，最可靠）；②请求级去重（幂等 token/nonce，短窗口内一次性）；③状态机（只允许 `PENDING → APPROVED`，重复调用直接返回当前状态）。
关键是「幂等键」要由客户端生成并随请求带上，服务端存储 + 原子占用。
**本项目落地**：三道都用了——CMS 签名请求的 `nonce` 用 `ConcurrentHashMap.putIfAbsent` 一次性占用、保留 13 分钟（`AuthInterceptor.java:44,180-194`）；检查点用 `MERGE`（Q41）；归因队列按 `campaignId + cohortDate + ageDay` **重复观测替换而非累加**（`README.md` 海外运营模块一节）——最后这条尤其关键，累加会让同一批收入被算两遍。
**追问**：nonce 表会不会无限增长？——会，所以 `USED_NONCES.size() > 4096` 时按时间戳淘汰（`:182-184`）；这是内存版的 TTL，生产该换 Redis。
「先查再插」为什么不幂等？——并发下两个请求都查到「不存在」，都插入。

### Q47. 常见限流算法？分别适合什么场景？（★★）
**标准答**：固定窗口（简单，临界点双倍突发）、滑动窗口（平滑，内存成本高）、漏桶（恒定速率，抗突发）、令牌桶（允许一定突发，最常用）。
分布式限流用 Redis + Lua 原子扣减，或网关层（Sentinel/Nginx）统一做。
**本项目落地**：本项目**没有引入限流组件**，用的是「业务配额」代替：日 LLM 调用上限默认 200（`agent_safety_policy.max_llm_calls_per_day`，clamp 到 10-10000）、日预算变动上限 500、单次调幅 20%（`AgentSafetyPolicyService.java:86-89`）。理由：这套系统的稀缺资源是「模型调用配额」和「预算变动额度」，不是 QPS——按天配额比按秒限流更贴合业务。
**追问**：令牌桶的桶容量和速率怎么定？——速率 = 长期平均 QPS，容量 = 允许的突发量。
限流和降级的关系？——限流是入口控制，降级是能力降级（如模型不可用转规则）。

### Q48. 为什么用消息队列？怎么选型？（★★）
**标准答**：三大价值：解耦（生产者不关心消费者）、削峰（把突发流量缓冲成平稳消费）、异步（主链路不等慢操作）。
选型：Kafka 吞吐最高、适合日志/流式；RocketMQ 事务消息与顺序消息强、适合业务；RabbitMQ 路由灵活、延迟低。
代价：一致性变最终一致、要处理重复消费（幂等）、要处理堆积与顺序。
**本项目落地**：本项目**没有 MQ**。渠道数据同步是 `@Scheduled` 定时拉取 + `@Async` 线程池执行（如 `TaboolaAdDataService:40-41`），智能体分析是请求内同步执行 + SSE 推进度。为什么不引：数据一致性要求「同一批日报要么全进要么全不进」，用 DB 事务比 MQ + 补偿简单得多；量级（32 站点/640 活动）也不需要削峰。
**追问**：MQ 怎么保证不丢消息？——生产确认 + 持久化 + 消费手动 ack + 重试队列。
重复消费怎么办？——消费端幂等（Q46）。

### Q49. CAP 和 BASE 怎么理解？本项目怎么取舍？（★★）
**标准答**：CAP 是网络分区下只能在一致性（C）和可用性（A）之间选，P 是必须接受的现实。BASE 是对 AP 的工程化：基本可用、软状态、最终一致。
大多数业务系统实际是「分区时选 A、平时尽量 C」，靠最终一致补偿。
**本项目落地**：单机主库（H2/MySQL），没有跨节点复制，所以平时是 CP：审批、记账、预算变动都在一个本地事务里，宁可失败也不写半截。真正的「最终一致」出现在**跨系统**：CMS 只管本地记录，外部平台执行标记为「待人工处理」，不保证与远端同步（`README.md` 规则页执行边界一段）。
**追问**：CP 系统可用性怎么保证？——主备 + 故障转移，代价是切换窗口内不可用。
为什么广告预算不能选 AP？——超投是真金白银，宁可不投也不能超。

### Q50. 分布式 ID 有哪些方案？（★★）
**标准答**：UUID（本地生成、无中心、无序）、数据库自增/号段（有序、依赖 DB）、雪花算法（时间 + 机器号 + 序列，趋势递增、依赖时钟）、Redis `INCR`（简单、多一次网络）。
选择看是否需要趋势递增（影响 B+ 树插入性能）和是否容忍时钟回拨。
**本项目落地**：`runId`、`turnId`、`checkpointId`、`nonce` 全用 `UUID.randomUUID()`（`AdOptimizationGraph.java:107`、`JdbcCheckpointSaver.java:89-90`）——因为这些 ID 只做标识、不做排序主键；`threadId` 是 `"run-" + runId`（`:124-126`），可读且能和业务 ID 对应。
**追问**：UUID 做主键有什么问题？——无序导致页分裂、占 16 字节；用 `UUID_TO_BIN` 或雪花更好。
雪花算法的时钟回拨怎么处理？——等待、或用备用机器号、或直接拒绝发号。

### Q51. 分布式事务有哪些方案？（★★★）
**标准答**：2PC/XA（强一致、性能差、协调者单点）、TCC（业务侵入大、要写 Try/Confirm/Cancel）、Saga（长事务补偿、无隔离性）、本地消息表/事务消息（最终一致，最常用）、最大努力通知。
工程上优先「避免分布式事务」：能放进一个库就别拆，能用状态机 + 补偿就别上 2PC。
**本项目落地**：本项目是**单库单事务**，不存在分布式事务——这是设计选择不是能力缺失。跨系统的「写」被显式挡在门外：智能体只读（8 个工具全只读，`AdvisoryTools.java:37-156`），预算执行走「审批 → ledger 记账 → 本地更新」，外部平台一律标「待人工处理」（`README.md` 规则页执行边界一段）。
**追问**：TCC 的空回滚和悬挂怎么解决？——事务控制表记录状态，Cancel 时判断 Try 是否执行过。
为什么 Saga 没有隔离性？——中间状态对外可见，需要业务上加锁或语义锁。

---

## 六、LLM 与 Agent

### Q52. Function Calling / Tool Calling 的本质是什么？（★★）
**标准答**：模型不执行函数，它只输出一个结构化的「调用意图」：函数名 + JSON 参数（OpenAI 协议里是 `tool_calls`）。
宿主程序解析后真正执行函数，把结果以 `tool` 角色消息回灌，模型再基于结果继续推理。所以安全性完全由宿主决定——模型能调什么、参数是否合法、结果是否可信，都是工程问题。
**本项目落地**：`AdvisoryTools` 的 8 个方法用 LangChain4j 的 `@Tool`/`@P` 声明，`LC4jToolService.builder().toolsFromObject(...)` 生成 `ToolSpecification`（`AdOptimizationGraph.java:110`），执行在 `toolExecutorNode`（`:408-436`）里通过 `toolNode.execute(request)` 完成，结果作为 `ToolExecutionResultMessage` 回灌。
**追问**：模型能调不存在的工具吗？——会，所以本项目对未知工具返回 `{"available":false,"reason":"未知工具"}`（`:570-571`）而不是抛异常。
工具描述重要吗？——极其重要，描述就是模型的「使用说明书」，写得含糊模型就会乱调。

### Q53. ReAct 循环是什么？终止条件怎么定？（★★）
**标准答**：ReAct = Reasoning + Acting：模型先想（或直接给调用意图）→ 调工具 → 观察结果 → 再想，直到不再请求工具或达到轮次上限。
工程上必须有**硬上限**：最大轮次、最大 token、最大时长，否则模型可能陷入「反复查同一个工具」的死循环。
**本项目落地**：优化图 `MAX_TOOL_ROUNDS = 5`（`AdOptimizationGraph.java:65`），条件边写的是 `hasPendingTools() && toolRound < MAX_TOOL_ROUNDS`（`:178-183`），超了就强制进护栏节点；Copilot 图上限 6 轮（`AdOpsCopilotGraph.java:51`）。护栏回环另外限 `MAX_REVISIONS = 1`（`:66`）——反思只允许一次，防止「改了还错、错了再改」无限循环。
**追问**：为什么 Copilot 给 6 轮、优化图只给 5 轮？——优化图是无人值守的批量任务，轮次直接等于 token 成本；Copilot 是交互式，用户能等。
轮次用完了但模型还想调工具怎么办？——强制结束并把已有信息交给护栏/最终节点，本项目就是这么做的。

### Q54. StateGraph 和条件边是怎么工作的？为什么用编译态图而不是 while 循环？（★★★）
**标准答**：StateGraph 是「节点 + 边 + 共享状态」的状态机：每个节点是 `state -> stateDelta` 的纯函数，边可以是固定的，也可以是条件边（根据 state 返回下一个节点名）。
编译期会校验所有节点/边可达、把图编译成可执行对象并生成 Mermaid 图；运行时按边跳转，状态在节点间合并。
比手写 while 循环的好处：拓扑显式可读、可生成文档、可接检查点、可流式观察每个节点。
**本项目落地**：`buildGraph()`（`AdOptimizationGraph.java:150-206`）里 8 个节点、4 条条件边；`CompileConfig` 里挂上 `checkpointSaver`（`:200-203`），再 `compiled.setMaxIterations(24)`（`:204`）防止回环把图跑飞。图还能导出 Mermaid（`:90-92`）直接贴进文档。
**追问**：状态怎么合并？——LangGraph4j 用 `SCHEMA` 定义 reducer，本项目节点返回 `Map<String,Object>` 增量、由框架合并；注意别在节点里改共享可变对象。
`maxIterations=24` 的依据？——图有两条回环（工具 ×5、反思 ×1），最坏路径约 20 步，24 是留了余量的硬保险。

### Q55. 检查点（Checkpoint）有什么用？Human-in-the-Loop 怎么落？（★★★）
**标准答**：检查点把「每个节点执行后的完整状态」持久化，用途：断点续跑、失败重放、审计「当时图停在哪」、人工审批后从该点继续。
HITL 的本质是「图在某个节点暂停，等人决策，再把决策注入状态继续」——所以状态必须可序列化、暂停点必须可定位。
**本项目落地**：`JdbcCheckpointSaver` 实现 `BaseCheckpointSaver`，表 `agent_checkpoint(thread_id, checkpoint_id, node_id, next_node_id, state_json)`，`threadId = "run-" + runId`，取最新时按 `created_at DESC, checkpoint_id DESC`（`JdbcCheckpointSaver.java:37-51`）。序列化失败有三级降级：直接序列化 → 逐字段 `String.valueOf` → 写 `{}` 并告警（`:129-144`）——**检查点写失败绝不能阻塞图执行**。人工审批不走图的中断，而是「图跑完产出 PENDING 方案 → 人工 review → 落 ledger 执行」，审批意见又通过 `historicalRuns` 工具回喂模型（情景记忆）。
**追问**：为什么不用内存 Map 存检查点？——重启就丢，且审计要求能追溯。
`state_json` 会不会很大？——会，所以工具输出先截断 6500 字符再进状态（`:559-577`）。

### Q56. 上下文窗口怎么管理？超了怎么办？（★★★）
**标准答**：手段按成本从低到高：工具输出截断/摘要、历史消息只保留最近 N 轮、把长文档做检索（RAG）按需注入、多级摘要（滚动总结）、最后才是换更大窗口的模型。
关键原则：**上下文是预算，不是垃圾桶**——无关内容进上下文不仅费钱，还会稀释关键指令（lost in the middle）。
**本项目落地**：四道闸：`MAX_TOOL_OUTPUT = 6500` 超过就截断并标注「…工具输出已截断」（`AdOptimizationGraph.java:559-577`）；`dailyReport` 最多 40 天（`AdvisoryTools.java:18,53`）；`historicalRuns` 只取近 5 次（`:122`）；`stageFindings/actionItems/experiments` 分别限 6/8/6 条（`:634-647`）。Copilot 的截断阈值放宽到 12000（`AdOpsCopilotGraph.java:52`），因为交互式场景用户愿意等。
**追问**：截断会丢信息怎么办？——先保证「结论级信息」不被截掉：本项目让工具返回聚合后的结构（ratio 已算好），而不是原始行。
为什么不用 RAG？——本项目**没有向量库**：情景记忆用结构化 SQL（近 5 次审批记录）比向量召回更准且可解释。

### Q57. 怎么让模型稳定输出结构化 JSON？（★★）
**标准答**：四层：①提示词写清 schema 并给示例；②用 `response_format=json_schema`（或 JSON mode）由服务端约束；③解析时容错——去 Markdown 围栏、取首 `{` 到末 `}`、失败降级；④解析后用 schema 校验（类型、枚举、边界），不合法就重试或拒绝。
**本项目落地**：`stripFences` 去围栏 → `indexOf('{')`/`lastIndexOf('}')` 截取 → Jackson 解析（`AdOptimizationGraph.java:616-663`）；解析出来后逐字段校验：`action` 必须在 `{HOLD, REDUCE, INCREASE}`、`changePercent` 绝对值不得超上限、`INCREASE` 必须正数、`HOLD` 必须 0（`:474-493`）；`confidence` 被 clamp 到 [0,1]、`riskLevel` 不在 `{LOW,MEDIUM,HIGH}` 就置 null。
**追问**：模型只回了散文怎么办？——`structuredJsonFound=false`，保留散文作为 `explanation`，决策交回规则护栏（`:661-662`），不硬解析。
为什么不直接信 JSON mode？——不是所有 OpenAI 兼容端点都支持，网关要能兼容残缺实现。

### Q58. 幻觉怎么抑制？（★★★）
**标准答**：三层：①**输入**——只喂真实数据（工具返回），提示词明确禁止编造；②**输出**——结构化约束 + 数值边界校验 + 引用证据；③**兜底**——人工审批、可回滚、灰度放量。
再加一条工程手段：让模型「先算再报」（给它试算工具），比让它心算靠谱得多。
**本项目落地**：提示词第 1 条就是「只能使用工具返回的真实数据和输入指标，严禁编造数字、国家或投放结果」（`AdOptimizationGraph.java:682`）；返回体强制带 `evidenceRefs`（引用具体日期/国家/规则）；护栏节点二次校验枚举与幅度；渠道未配置时 `report_data_config` 缺失，建议一律压回 HOLD（`README.md` 「每个渠道都有报表配置」一条）——这是最狠的一招：**没数据就不许给建议**。
**追问**：怎么发现模型在编？——让它给证据引用，然后程序侧校验引用是否在工具返回里出现过（本项目是「要求给」，还没做「自动核对」，这是可改进项）。
温度调低能治幻觉吗？——能降低随机性，不能消除「自信地编」。

### Q59. 模型降级与重试怎么设计？（★★★）
**标准答**：重试只对**瞬态错误**（429/5xx/超时/连接重置）做，且要指数退避 + 抖动 + 上限；4xx 参数错误重试是浪费。降级链：主模型 → 备选模型 → 去工具重试 → 规则模板。
关键细节：关掉 SDK 自带重试，否则「SDK 重试 × 你的重试」会放大成 N 倍流量把上游打挂。
**本项目落地**：`AiModelGateway` 用 `maxRetries(0)` 关 SDK 重试（`:227`），自己实现「瞬态才重试 1 次、间隔 600ms」（`:179-213`，`isTransient` 白名单在 `:245-250`）；主模型失败切备选模型，两者都失败才抛，异常信息拼接两侧原因（`:163-176`）。更上层：`strategy_planner` 带工具失败 → 去工具重试一次（`AdOptimizationGraph.java:366-390`）→ 仍失败降级规则模板（`:392-402`）→ 图最外层再 catch 兜底成 `GraphOutcome.failed`（`:136-144`）。
**追问**：为什么重试间隔只有 600ms？——单次分析的总预算是「用户能等」，长退避不如快速切备选。
降级了用户知道吗？——知道，`FAILURE_NOTE` 和 telemetry 的 `DEGRADED/RECOVERED` 状态都会透出到 SSE 与 trace 表。

### Q60. Token 成本怎么控？（★★）
**标准答**：三个杠杆：①减少输入（截断、摘要、少喂无关上下文）；②减少调用次数（缓存、批量、少几轮工具）；③换更便宜的模型（分级路由：简单任务小模型）。
另外要能**计量**：按 run/session/node 记录 input/output token，否则优化无从谈起。
**本项目落地**：`agent_execution_trace` 记录每个节点的 `input_tokens/output_tokens/total_tokens/tool_calls/duration_ms`（`AgentTelemetryService.java:31-63`），`summary()` 聚合总量与分节点均值（`:156-214`）；成本硬上限是「日 LLM 调用上限默认 200」（`AgentSafetyPolicyService.java:149-166`），超了 `isLlmBudgetExhausted()` 就拦；再加上 5 轮工具上限和 6500 字符截断。
**追问**：怎么按租户/活动分摊成本？——现在只能按 run 维度统计（trace 表有 run_id/session_id），按活动分摊需要把 campaignId 落进 trace，是待办。
prompt 缓存有用吗？——有用（相同前缀复用），但兼容端点支持程度不一。

### Q61. 给 Agent 设计工具的原则？（★★）
**标准答**：①**单一职责**：一个工具一件事，名字动词开头；②**参数少而明确**：能用枚举就别用自由字符串；③**返回结构化且已聚合**：把 ratio 算好再返回，别让模型做算术；④**幂等/只读优先**：写操作必须显式、可审计；⑤**失败返回结构化错误**：`available:false + reason`，让模型能自我修正；⑥**输出有上限**：防上下文爆炸。
**本项目落地**：8 个工具全是只读、全绑定单次分析上下文（`AdvisoryTools.java:12-16` 的类注释就写明「strictly read-only and parameterized」）；`simulateBudget` 只算不写库（`:133-156`）；`effectiveLimit()` 把「用户请求幅度、规则上限、安全策略」三者取最小（`:158-160`）——**默认最保守**。
**追问**：为什么不做写工具？——广告预算写操作不可逆；先做「可解释的建议」，再做「可审计的执行」。
工具太多会怎样？——选择困难 + 描述占满上下文，通常 5-15 个是甜点区。

### Q62. 多智能体怎么分工？什么时候不该用多 Agent？（★★）
**标准答**：分工依据是「子任务有独立上下文、独立工具集、独立输出契约」。反例：串行几步就能完成的事，拆成多个 Agent 只是把一次 LLM 调用变成 N 次，延迟和成本翻倍还增加不一致风险。
设计上要定义清楚 Agent 间的交接物（结构化），否则就是「把幻觉从一个人传染给一群人」。
**本项目落地**：五 Agent 闭环各管一段且输出是结构化报告：Monitor（0-100 健康分 + 阈值告警）→ Audience（人群段 + Lookalike）→ Creative（3-5 条变体）→ Bidding（`eCPM` 公式写进返回体）→ Optimize（暂停低分素材 + 预算重分配 + A/B 启停）；图层面只有 `research_specialist`（DEEP 模式的归因研究）与 `strategy_planner` 两个 LLM 角色，其余是确定性计算——**能用代码算的绝不用模型算**。
**追问**：为什么 Bidding 的公式要放进返回体？——让运营能复核算法，而不是只看到黑盒的「建议出价」。
多 Agent 的一致性怎么保证？——交接物结构化 + 单一决策出口（护栏节点）。

### Q63. Agent 系统怎么评测？（★★★）
**标准答**：分三层：①**单测**——mock 模型，断言状态机流转、护栏、落库副作用；②**离线回放**——用历史真实数据 + 固定 prompt 跑打分（准确性、引用率、越界率）；③**线上指标**——采纳率、人工驳回率、平均轮次、token 成本、P95 延迟。
关键：不要用「模型输出文案」做断言，那是脆弱的；要断言可验证的约束。
**本项目落地**：10 个测试类里 `LangGraphCommercialAgentTest`（端到端 + 结构化输出 + 护栏拦截）、`AgentGuardrailsAndReplayTest`（护栏边界 + 检查点重放）就是前两层；线上指标靠 `agent_execution_trace` 聚合。另外有 `OfflineReplayEvaluator` 与 `SignalScorecard` 做离线评分。
**追问**：模型升级怎么回归？——固定一批「金标准」case 跑离线评测，看护栏拦截率与越界率的变化。
怎么衡量「建议质量」？——短期看人工采纳率，长期看审批通过后的实际 ROAS 变化（本项目在合成数据上无法证明真实提升，这点必须诚实）。

---

## 七、广告业务算法

### Q64. eCPM 怎么算？为什么是排序的核心指标？（★★）
**标准答**：`eCPM = pCTR × pCVR × TargetCPA × 1000`（oCPC 口径），本质是「预估一次展示能带来多少收入」。
广告平台按 eCPM 排序而不是按出价排序，因为出价高但没人点的广告对平台是负价值。乘以 1000 是把「每次展示」换算成「千次展示」。
**本项目落地**：`FiveAgentOrchestrator.java:594-635` 就是这么算的：`pCtr` 取 `max(0.002, ctr/100)`、`pCvr` 取 `max(0.005, cvr/100)` 做下限保护，`baseEcpm = pCtr × pCvr × targetCpa × 1000`；然后把整条公式 `String.format` 进 `BiddingReport.formulaText`（`:628-630`）返回给运营复核。
**追问**：为什么要有 pCTR/pCVR 下限？——样本少时观测 CTR 可能是 0，直接乘会让 eCPM 归零、永远拿不到量，形成「越没量越没量」的死锁。
eCPM 和 CPM 什么关系？——CPM 是实际计费价，eCPM 是预估价值，用于排序。

### Q65. pCTR/pCVR 为什么需要校准？怎么做？（★★★）
**标准答**：模型只要保证**排序**正确就能提升 eCPM，但要算钱、算 ROI 就必须保证**绝对值**准确——这就是校准（calibration）。
手段： Platt scaling / isotonic regression、分桶校准、贝叶斯平滑（新广告用先验 + 少量观测）、按时间段滚动重训。
评估看 logloss / 校准曲线（预测 0.1 的样本里实际点击率是否约 10%）。
**本项目落地**：本项目**不做模型训练**，pCTR/pCVR 直接用历史观测的 CTR/CVR（`monitor.ctrPct()/100`，`:599-600`），并用下限 + 动态倍率补偿：ROAS 达标 1.2 倍以上 ×1.30、略低于目标 ×0.85、显著低于或亏损 ×0.60（`:606-620`）。这是「没有模型时的工程近似」，面试要说清楚它不是 ML 预估。
**追问**：冷启动没有历史数据怎么办？——用同类活动/同站点的先验，本项目用 `peerBenchmarks` 工具取同站点基准。
校准误差大有什么后果？——出价系统性偏高（超投）或偏低（拿不到量）。

### Q66. ROAS 是什么？和 ROI 有什么区别？（★★）
**标准答**：`ROAS = 收入 / 广告花费`（分母只是媒体花费）；`ROI = (收入 - 成本) / 成本`（分母含全部成本）。
ROAS 是投放侧指标，1.0 是盈亏平衡线（还没算商品成本）；ROI 是经营侧指标。混用会导致「ROAS 2.0 看着很赚，实际亏钱」。
**本项目落地**：全链路用 ROAS：告警阈值 `<1.0`（`README.md`/`project-map.md` 的 Monitor 定义）、规则里 `ROI < 0.85` 之类的阈值（`PlatformEconomics.RULES`）、护栏里 `targetRoas` 参与幅度上限计算。归因队列把累计收入按 `ageDay` 存，就是为了让 ROAS 随回收窗口变化而不是当天定生死。
**追问**：ROAS 达标但现金流为负可能吗？——可能，回款周期长或退款率高。
目标 ROAS 怎么定？——从毛利反推，`目标 ROAS = 1 / 毛利率`（简化口径）。

### Q67. CPA 和 TargetCPA 出价怎么工作？（★★）
**标准答**：CPA = 花费 / 转化数。oCPC/oCPA 出价是「告诉平台我愿意为一次转化付多少」，平台用 pCVR 反推点击出价，再用 pCTR 反推千次展示价。
风险：转化回传延迟会导致平台学习偏差；目标定太低拿不到量，定太高必亏。
**本项目落地**：`targetCpa = monitor.targetCpa()`，建议出价 `rawBid = targetCpa × 0.65 × 倍率`，硬上限 `≤ 80% TargetCPA`（`:622-625`）——留 20% 缓冲是因为转化回传有延迟，按满额出价容易在回收前超投；`capApplied` 会显式告诉运营「这次是撞上限了」而不是悄悄截断。
**追问**：为什么系数是 0.65？——经验值，给波动留空间；真实系统应该用回收数据回归出来。
CPA 和 CPL 区别？——CPL 是单个线索（leads），CPA 是单个转化（含购买/注册等最终动作）。

### Q68. LTV 和同期群分析？（★★★）
**标准答**：LTV 是单个用户生命周期总价值；同期群（cohort）把同一时间获取的用户分组，看每组随 `ageDay` 的累计收入曲线——这样才不会被「新老用户混在一起」的平均值骗。
`LTV/CAC > 3` 是常见的健康线（粗略经验）；LTV 要和回收周期一起看。
**本项目落地**：归因队列按 `campaignId + cohortDate + ageDay` 保存**累计收入**，重复观测替换而非累加；至少 3 个年龄点且到 D7 才做对数趋势拟合，拟合不足不出预测（`README.md` 归因队列一段）。这条设计的价值在于：不预测比乱预测好——运营看到空白会去补数据，看到编的预测会做错决策。
**追问**：为什么用对数拟合？——回收曲线前期陡、后期平，对数/幂律比线性更贴合；但样本不足时任何拟合都是噪音。
同期群要多细？——至少按「获取日期 × 渠道」，否则归因不可解释。

### Q69. 归因窗口是什么？为什么重要？（★★★）
**标准答**：归因窗口是「用户点击/浏览广告后多长时间内的转化算给这条广告」。窗口越短越保守（漏掉长决策周期），越长越激进（把自然转化算成广告功劳）。
平台默认不同（Google 搜索 30 天点击、Meta 7 天点击 + 1 天浏览等），跨平台对比必须先对齐窗口。
**本项目落地**：本项目选了「按 ageDay 存累计观测、不猜窗口」的做法：D7/D30 未观测就留空，且**不做跨平台窗口换算**；报表里的收入是「归因队列里当前已观测到的累计值」。这是诚实但保守的口径——所以我们在文档里明确写「合成工作区的数字不能当真实客户效果」（`project-map.md` 的「口径纪律」）。
**追问**：多触点归因怎么做？——线性/时间衰减/Shapley，但都需要用户级路径数据，本项目只有活动级日报。
为什么不能简单把各平台转化相加？——会重复归因（同一用户被两个平台都记一次）。

### Q70. A/B 实验怎么判定胜负？（★★★）
**标准答**：先定指标与最小可检测效应（MDE）→ 算所需样本量 → 随机分流 → 跑够周期（覆盖完整周内季节性）→ 做显著性检验。
比例类指标（CTR/CVR）用双样本比例检验（z 检验）；多组比较要做多重检验校正（Bonferroni：`p × 比较次数`），否则「5 个变体总有一个显著」。
不要偷看（peeking）提前下结论，会显著抬高假阳性。
**本项目落地**：`OverseasEvidenceService.java:98-113` 实现了 CTR 双样本 z 检验 + Bonferroni 校正：`z = |ctr2-ctr1|/se`，`adjusted = min(1, p × max(1, arms.size()-1))`，且**周期不一致或样本不足直接判 INCONCLUSIVE**，不做正态近似。A 组为对照、B/C 为实验组，流量分派仍在广告平台做，登记草稿不自动分流（`README.md` 创意工坊实验看板一段）。
**追问**：为什么样本不足不能判？——正态近似在小样本下失效，会把噪声当信号。
为什么要跑满一周？——周内效应（工作日 vs 周末）会系统性污染结果。

### Q71. 预算平滑和节奏控制怎么设计？（★★★）
**标准答**：目标是把日预算按时段均匀花完（或按业务节奏加权），避免「上午烧完、下午断量」。
手段：按小时分配权重（分时段出价系数）、预测当日消耗曲线并动态调价、设置 pacing 目标（如「到 12 点应花 40%」）、超速就降价、落后就提价。
**本项目落地**：本项目是「准实时」而非「实时竞价」：日预算变动有三道闸——单次调幅上限默认 20%（clamp 1-30）、日累计变动上限默认 500、Kill Switch（`AgentSafetyPolicyService.java:111-132`）；预算是按天调而不是按小时调，因为 AFS 的投放动作是「改广告组日预算」，不是 bid 级别的实时出价。
**追问**：为什么单次调幅要限 20%？——防止重置广告平台的学习期（大幅改动会触发重新学习，效果短期崩塌），代码里 `:124` 的理由就写在异常消息里。
500 的日上限怎么来的？——业务侧定的风控额度，宁可人工多批几次。

### Q72. 为什么出价有上限？学习期是什么？（★★）
**标准答**：学习期是平台在投放初期/重大改动后重新探索人群的阶段，此阶段成本波动大、效果不稳定；频繁或大幅改动会让广告组反复进入学习期，永远学不出稳定模型。
所以工程上要「小步快跑」：单次改动幅度受限、改动后观察足够时间再动。
**本项目落地**：出价硬上限 `≤ 80% TargetCPA`（`FiveAgentOrchestrator.java:623`）+ 单次调幅 ≤ 20%（`AgentSafetyPolicyService.java:122-125`）+ 反思回环最多 1 次（`AdOptimizationGraph.java:66`），三者共同保证「不会因为模型一次激动就把预算翻倍」。规则侧也有对应约束：定时评估同一规则/活动每天最多触发一次（`README.md` 规则页一段）。
**追问**：学习期一般多久？——平台口径不同，常见「7 天或 50 次转化」取先到者。
模型说「加 50% 效果最好」怎么办？——护栏直接拦，改成 20% 并让模型重新解释，这就是 `changePercent 超出上限` 那条 violation。

---

## 八、工程与安全

### Q73. 开放 API 的签名鉴权怎么设计？（★★）
**标准答**：要素是「密钥 + 时间戳 + 随机串 + 参数摘要」，服务端用同样算法重算并比对；排序保证参数顺序不影响结果。
标准做法是 HMAC-SHA256（`HMAC(secret, canonicalString)`），因为 MD5 已被证明碰撞不安全、且裸 MD5 拼接存在长度扩展攻击面。
**本项目落地**：**必须诚实说清楚**：本项目是 `md5(salt & 排序后的头 KV & bodyMd5)`——`TreeMap` 保证头顺序（`AuthInterceptor.java:117-126`），JSON 写操作额外要求 `admin-body-md5` 头且必须与真实 body 的 MD5 一致（`:159-178`）。为什么用 MD5 而不是 HMAC？——**历史开放 API 契约如此**（老 CMS 客户端已按此实现），换算法等于让所有存量客户端同时升级。新版内部接口应该用 HMAC-SHA256，这是「兼容存量 vs 新增安全」的现实取舍，别吹成最佳实践。
**追问**：为什么要把 body 的 MD5 也签进去？——只签 header 的话，攻击者能改 body 而不被发现。
salt 从哪来？——生产是账号密码/密钥；本地工作区额外允许 `data/local-admin.key` 兜底（`:130-137`），仅本地。

### Q74. 怎么防重放攻击？（★★★）
**标准答**：时间戳窗口 + nonce 一次性 + 签名覆盖全部可变内容。三者缺一不可：只有时间戳，窗口内可重放；只有 nonce，攻击者可自己生成新 nonce。
nonce 存储必须「原子占用 + 有过期」，否则并发下能双花。
**本项目落地**：窗口 12 分钟（`SIGNATURE_WINDOW_MS = 12 * 60 * 1000L`，`:43`），nonce 保留 13 分钟（`:44`），用 `ConcurrentHashMap.putIfAbsent` 原子占用（`:185`），超过 4096 条按时间戳清理（`:182-184`）。
**追问**：为什么窗口是 12 分钟而不是 5 分钟？——客户端时钟漂移 + 老客户端重试策略，太短会大量误拒；这是业务妥协。
多实例部署怎么办？——现在的 `ConcurrentHashMap` 是单机的，多实例必须换成 Redis `SET NX EX`，这是明确的待办（本项目是单机部署所以没做）。

### Q75. AES-GCM 怎么用？为什么不能用 ECB？（★★★）
**标准答**：GCM 是 AEAD（带认证的加密），同时提供机密性和完整性；参数：12 字节 nonce、128 位认证标签、可选 AAD。
**nonce 绝对不能重复**——同一密钥下重复 nonce 会泄漏明文异或关系并可能被伪造，这是 GCM 的致命失效模式。
ECB 对相同明文块产生相同密文，会泄漏数据结构（经典的「企鹅图」），任何场景都不该用。
**本项目落地**：`AES/GCM/NoPadding`，每次加密随机 12 字节 nonce，`GCMParameterSpec(128, nonce)`，并把 `provider:alias` 作为 AAD（`ChannelCredentialController.java:395-409`）——AAD 让「密文被挪到另一个 provider/alias 下」直接解密失败。落库格式是 `base64(nonce):base64(cipher)`（`:400-401`）。
**追问**：为什么用 AAD 绑 provider+alias？——防止攻击者把 A 渠道的密文复制到 B 渠道行上冒用。
GCM 有 16 字节 tag，存哪？——`doFinal` 输出里自带（密文 + tag），所以格式是 nonce + 密文+tag。

### Q76. 密钥怎么管理？（★★）
**标准答**：三原则：不硬编码、与数据分离存储、可轮换；读取要限制权限，日志里必须脱敏。
生产用 KMS/Secrets Manager，本地用受限权限的文件；密钥文件绝不进 git。
**本项目落地**：`loadKey()` 首次启动生成 32 字节随机密钥写入文件，POSIX 文件系统上设 `rw-------`，长度不是 32 字节直接抛异常（`:411-433`）；密钥文件与业务库分离、`data/` 已在 `.gitignore` 里；全链路 `redact()` 把 apiKey 替换成 `[凭据已隐藏]`（`AiModelGateway.java:252-258`）。
**追问**：密钥怎么轮换？——理想是「新密钥加密新数据 + 旧密钥解密存量 + 后台重加密」；本项目还没做轮换，属于待办，面试时可以直接承认。
为什么不用环境变量存密钥？——会被 `ps`/崩溃转储/docker inspect 泄露；文件 + 权限是次优解。

### Q77. SSE 是什么？和 WebSocket 怎么选？（★★）
**标准答**：SSE 是基于 HTTP 的单向（服务端→客户端）流，`Content-Type: text/event-stream`，天然支持断线重连（浏览器 `EventSource`）、穿过大多数代理；WebSocket 是全双工，需要协议升级。
只需要「服务端推进度」就用 SSE，简单得多；需要双向交互才用 WebSocket。
**本项目落地**：`SseEmitter(180_000L)` 超时 180 秒（`AgentTelemetryService.java:67`）；按 runId 维护 `CopyOnWriteArrayList`；**迟到订阅先回放历史节点**（`:79-100`），这样用户刷新页面也能看到已执行的节点；`completeRunStream` 发 `done` 事件并 complete 所有 emitter（`:103-118`）。
**追问**：SSE 断连怎么防泄漏？——`onCompletion/onTimeout/onError` 三个回调都挂移除逻辑（`:76-78`），`publish` 发送失败时从列表剔除、列表空则移除整个 key（`:120-130`）。
为什么是 180 秒？——一次分析的最长预期耗时；超时后客户端可重连并用历史回放补齐。

### Q78. 可观测性怎么做？（★★）
**标准答**：三支柱：Metrics（聚合指标）、Logging（离散事件）、Tracing（请求链路）。AI 系统还要加两维：token 成本与工具调用链。
要点是「能回答三个问题」：出了什么问题、影响哪些请求、下一次怎么复现。
**本项目落地**：`agent_execution_trace` 落每个节点的 run/node/status/model/token/tool_calls/duration/summary（`AgentTelemetryService.java:31-63`）；`summary()` 聚合节点执行数、去重 run/session 数、token 总量、工具调用总量、分节点平均耗时（`:156-214`）；SSE 把同一份数据实时推给前端。日志侧所有异常都经 `redact()`，`describe()` 截断到 260 字符防止日志爆炸（`AiModelGateway.java:260-269`）。
**追问**：没有 OpenTelemetry/Micrometer？——本项目没有接（单机、无 Prometheus），靠 DB 表 + 前端看板；这是「够用就好」的取舍。
怎么定位一次慢分析？——按 runId 查 trace，看哪个节点 duration 最大。

### Q79. 三级安全策略是怎么设计的？为什么默认 COPILOT？（★★★）
**标准答**：分级授权的通用模式是「观察 → 建议 → 执行」，每一级对应不同的信任成本和回滚成本。
四道闸的顺序很重要：先看全局熔断（最高优先级），再看模式，再看单次幅度，最后看日累计——因为日累计需要查库，能省一次查询就省。
**本项目落地**：`AgentSafetyPolicyService.assertExecutionAllowed`（`:111-132`）：①Kill Switch → 423 LOCKED；②SHADOW → 409 仅允许分析与试算；③单次幅度 > `maxSingleChangePct` → 409（理由写在消息里：防止重置学习期）；④今日已提交变动 + 本次 > `dailyBudgetDeltaCap` → 409。「今日已提交」取 `optimization_ledger` 里 `action_type='EXECUTE' AND status='COMMITTED'` 的 `SUM(ABS(after_budget - before_budget))`（`:134-147`）——**按实际落库的变动算，不是按建议算**。
**追问**：为什么默认 COPILOT 而不是 AUTONOMOUS？——LLM 的错不是随机错，是「自信地编」；先用人工审批积累误报率，再逐步放权，这是可运营的信任曲线。
Kill Switch 用 423 有什么讲究？——423 LOCKED 语义是「资源被锁定」，比 403 更准确，客户端能针对性提示。

### Q80. 四眼原则（Four Eyes）怎么落地？（★★）
**标准答**：关键操作必须「提交人 ≠ 审批人」，靠角色分离 + 强制校验实现，不能只靠流程约定。
技术手段：不同角色、不同账号、审批接口只对 REVIEWER 开放、密码强度校验、审计留痕。
**本项目落地**：`StaffSecurity`（`:19-55`）用 `@ConditionalOnProperty` 开关，两个账号 `operator` / `reviewer`，BCrypt 存密码，启动即校验「≥12 字符、≤72 UTF-8 字节（BCrypt 上限）、两者不能相同」（`:27-31`）；路由上 `POST /cms/agent/runs/*/review` 需 `REVIEWER`，其余 `/cms/agent/**` 写操作需 `OPERATOR`，`STATELESS` + HTTP Basic，`anyRequest().denyAll()` 兜底。
**追问**：为什么密码不能超过 72 字节？——BCrypt 只取前 72 字节，超出部分被静默忽略，等于削弱强度。
CSRF 怎么处理的？——只对 JSON 的 agent 写接口豁免（`isAgentApiMutation`，`:57-67`），其他路径不豁免。

### Q81. Prompt 注入和工具越权怎么防？（★★★）
**标准答**：Prompt 注入是「用户输入里的指令覆盖了系统指令」。防御不能只靠提示词，必须靠**权限边界**：模型能调的工具、能读的数据、能写的字段，在代码层限制死。
原则：模型输出永远当作「不可信输入」处理，和对待用户输入一样要做校验。
**本项目落地**：三层：①工具全只读、参数化，模型无法通过工具改预算（`AdvisoryTools` 类注释与实现）；②模型输出的 `action/changePercent` 一律过护栏（`AdOptimizationGraph.java:474-493`），越界触发反思而不是执行；③真正执行要人工审批 + 安全策略四道闸（Q79）。另外工具输出截断也顺带限制了「用超长工具返回淹没系统指令」的注入面。
**追问**：用户在三方报表数据里写「忽略以上指令」会怎样？——它会进上下文，但模型即使被带偏也只能输出一个被护栏校验的 JSON，改不了真实预算。
为什么不能只在提示词里写「不要听用户的话」？——提示词是软约束，攻击者可以不断尝试；权限是硬约束。

### Q82. 依赖与敏感信息治理？（★★）
**标准答**：依赖：锁版本、定期扫 CVE（`dependency-check`/Dependabot）、不用来路不明的包；敏感信息：不进代码、不进日志、不进前端、不进异常栈。
**本项目落地**：`pom.xml` 锁死版本（Spring Boot 2.7.0、LangChain4j 1.0.1、LangGraph4j 1.5.14）；凭据 AES-GCM 落库（Q75）；异常经 `redact()`（`AiModelGateway.java:252-258`），工具异常也做 `replace('"','\'')` 防 JSON 破坏（`AdOptimizationGraph.java:572-576`）；前端只拿 `accessKeyIdMasked` 这类脱敏字段（`ChannelCredentialController.java:190`）。
**追问**：日志里怎么防密钥泄漏？——统一出口脱敏，而不是靠开发者自觉；本项目把 `redact` 做在网关层，所有调用方自动受益。
依赖冲突怎么排查？——`mvn dependency:tree`，排除传递依赖。

---

## 九、本项目没用到但要会答的高频题

> 这一节的作用：面试官常问「你们为什么不用 X」。答「不需要」必须给出**量化理由**，不能只说「没做过」。

- **Kafka / RocketMQ**：用于异步解耦、削峰、流式处理。本项目是单机后台系统，渠道同步用 `@Scheduled` 拉取 + `@Async` 线程池，量级 32 站点 / 640 活动，引入 MQ 只会多一个必须运维的组件和一套最终一致补偿逻辑。若将来要做「实时出价回调」或多实例并行消费，才需要它。
- **Elasticsearch**：用于全文检索与复杂聚合。本项目报表查询都是结构化条件过滤（`custom_channel_id + date`），MySQL/H2 的 B+ 树与 `sum()` 聚合足够；搜索场景只有 CMS 后台的模糊匹配，`like concat('%',#{kw},'%')` 在 640 活动规模下毫秒级。数据涨到千万行或要相关性排序时才值得上 ES。
- **分库分表**：解决单库容量与写入瓶颈。本项目最大表是 `adsense_data`（覆盖 1341 个自然日），单表完全放得下；分片会破坏「跨活动聚合报表」和「日累计变动 SUM」这两类查询——**分片键选 campaignId 则按天汇总跨片，选 date 则活动维度跨片**，都疼。真要做，先做冷热分离（历史归档到独立库）而不是分片。
- **Kubernetes**：解决多实例编排、弹性伸缩、滚动发布。本项目是单机 `java -jar` 形态（端口 9901/9903），有状态的东西太多：本地 H2 文件库、`data/local-admin.key`、`data/` 目录下的密钥与备份。上 K8s 意味着先要把这些状态外置，属于「换架构」而不是「加部署」。
- **向量库 / RAG**：解决「海量非结构化知识按语义召回」。本项目的「记忆」是结构化的：过往前 5 次分析与人工审批意见用 SQL 取（`historicalRuns`），比向量召回更准、可解释、零额外依赖。没有「几千篇文档要问答」的场景，硬上 RAG 只会增加不确定性。
- **Redis 集群 / 多级缓存**：本项目的缓存是进程内一个 volatile 字段（数据日期），单机下正确且零成本；**但这也意味着多实例部署时 nonce 去重和分布式锁会失效**——这是本项目明确的架构边界，面试时主动说出来比被问出来好。
- **Seata / 分布式事务框架**：单库单事务，没有跨库写；跨系统的写被设计成「待人工处理」而不是自动补偿。
- **服务网格 / API 网关**：没有多服务，鉴权由 `AuthInterceptor` 在进程内做；网关的限流、熔断、灰度能力在这里没有落脚点。
- **CI/CD 平台化**：项目是本地构建（`mvn clean package`）+ PowerShell 启动脚本（`Run-LocalCms.ps1`），没有流水线；测试靠 `mvn test`。这是「单人项目」的合理形态，不是能力缺口。
- **虚拟线程 / Project Loom**：JDK 17 不提供（需 21+）；本项目的并发压力来自「少量长耗时 LLM 调用」，用有界线程池 + 日调用配额控制比换运行时更直接。
