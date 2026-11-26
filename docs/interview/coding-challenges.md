# AFS-CMS 面试手撕代码题（10 题）

> 面试现场「写代码」环节题库。每题都从本项目真实代码里长出来，零依赖，`javac X.java && java X` 直接可跑；每题至少有 1 处取值对应 `src/main/java/com/afs/**`。难度：★ 热身（5 分钟）｜★★ 常规（8-10 分钟）｜★★★ 区分度（15 分钟）。评分：写得出 60 分；边界主动提 ≥3 条 80 分；能讲清「为什么不换另一种写法」90 分。

## 题目 1：线程安全的滑动窗口限流器（★）
**场景**：开放 API `/open/**` 被老 CMS 客户端固定频率轮询，`AuthInterceptor` 只做签名校验、没有频控。现在按 apiKey 加一道「每分钟 60 次」的入口限流：`tryAcquire(key)` 放行返回 `true`，否则 429；要求任意 60 秒窗口内不超过 60 次，且限流器自身不能成为内存泄漏点。
**考察点**：滑动窗口 vs 固定窗口、锁粒度、`computeIfAbsent` 的原子范围、过期桶清理、时钟回拨。
**参考实现**：
```java
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
public final class SlidingWindowRateLimiter {
    static final int LIMIT = 60;                            // 目标：每分钟 60 次（项目当前只做签名、未做频控）
    static final long WINDOW_MS = 60_000L;                  // 窗口 60s
    private final ConcurrentHashMap<String, ArrayDeque<Long>> buckets = new ConcurrentHashMap<>();
    boolean tryAcquire(String key, long now) {
        if (buckets.size() > 4096) buckets.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast() <= now - WINDOW_MS);   // 防泄漏
        var stamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (stamps) {                             // 只锁一个 key，不同 apiKey 互不阻塞
            while (!stamps.isEmpty() && stamps.peekFirst() <= now - WINDOW_MS) stamps.pollFirst();   // 出窗即出队
            if (stamps.size() >= LIMIT) return false;
            stamps.addLast(now);
            return true;
        }
    }
    public static void main(String[] args) throws Exception {
        var limiter = new SlidingWindowRateLimiter();
        long now = 1_700_000_000_000L;                     // 固定时钟，结果可复现
        var granted = new java.util.concurrent.atomic.AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        for (int i = 0; i < 160; i++) pool.execute(() -> { if (limiter.tryAcquire("open-api", now)) granted.incrementAndGet(); });
        pool.shutdown(); pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("granted=" + granted.get() + " next=" + limiter.tryAcquire("open-api", now) + " after=" + limiter.tryAcquire("open-api", now + WINDOW_MS + 1));   // 60 false true
    }
}
```
**复杂度**：时间 O(1) 均摊（每个请求最多出队一次）；空间 O(keys × 60)。
**边界与坑**：
- 固定窗口在 59s→61s 交界会放过 2N 次，滑动窗口必须按时间戳出队；半开区间用 `<= cutoff` 还是 `< cutoff` 要定死，否则用例偶发失败。
- 锁必须加在 key 粒度：全局锁会让所有 apiKey 串行；`computeIfAbsent` 只保证「建桶」原子，不保证桶内操作原子，所以清理也要拿同一把桶锁（演示里省了，生产上要加）；4096 这个阈值与 `AuthInterceptor` 的 nonce 表同量级。
- `System.currentTimeMillis()` 受 NTP 回拨影响，回拨后 `cutoff` 变小会把已出队的历史重新算进窗口；生产上用单调时钟算窗口、墙上时钟只做审计。
**面试官追问**：
1. 为什么存时间戳而不是计数器？——语义是「任意 60s 内 ≤N」，必须能回答每个请求的入队时刻，计数器只能近似。
2. 100 万 key 还这么写吗？——不行，换 Redis `ZSET` + `ZREMRANGEBYSCORE`，或本地 60 个 1s 分桶把内存压到 O(keys)。

## 题目 2：开放 API 请求签名（TreeMap + MD5 + 时间窗 + nonce）（★★）
**场景**：老 CMS 客户端的开放接口用「盐 + 排序后的头 + body 的 MD5」做签名，服务端在 `AuthInterceptor` 里复算。签名原文为 `path?query & method & k1=v1&k2=v2... & salt &`，头带 `admin-timestamp`/`admin-nonce`/`admin-signature`，时间窗 12 分钟，nonce 一次性、保留 13 分钟。要求 `verify()` 拒绝篡改、超窗、重放三类请求。
**考察点**：`TreeMap` 保证排序确定性、拼接歧义、双向时间窗、nonce 去重原子性、常量时间比较。
**参考实现**：
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
public final class OpenApiSigner {
    static final long WINDOW_MS = 12 * 60 * 1000L;             // 12 分钟
    static final long NONCE_TTL_MS = WINDOW_MS + 60_000L;      // 13 分钟，留 1 分钟冗余
    private final String salt;
    private final Map<String, Long> usedNonces = new HashMap<>();
    OpenApiSigner(String salt) { this.salt = salt; }
    String sign(String method, String uri, Map<String, String> headers) {
        var sorted = new TreeMap<String, String>();            // 字典序是跨语言客户端唯一共同约定
        headers.forEach((k, v) -> { if (k.startsWith("admin-") && !k.equals("admin-signature")
                && v != null && !v.isBlank()) sorted.put(k, v); });
        var sb = new StringBuilder(uri).append('&').append(method).append('&');
        sorted.forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
        return md5Hex(sb.append(salt).append('&').toString());
    }
    synchronized boolean verify(String method, String uri, Map<String, String> headers, long now) {
        String nonce = headers.get("admin-nonce"), sig = headers.get("admin-signature");
        if (nonce == null || nonce.isBlank() || sig == null || sig.isBlank()) return false;
        if (Math.abs(now - Long.parseLong(headers.getOrDefault("admin-timestamp", "0"))) > WINDOW_MS) return false;
        if (!MessageDigest.isEqual(sign(method, uri, headers).getBytes(StandardCharsets.UTF_8),
                sig.toLowerCase().getBytes(StandardCharsets.UTF_8))) return false;   // 常量时间比较
        usedNonces.entrySet().removeIf(e -> e.getValue() < now - NONCE_TTL_MS);
        return usedNonces.putIfAbsent(nonce, now) == null;     // 先验签再烧 nonce，防被顶掉
    }
    static String md5Hex(String text) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static void main(String[] args) {
        var s = new OpenApiSigner("salt");
        var h = new TreeMap<>(Map.of("admin-timestamp", "1700000000000", "admin-nonce", "n-1", "admin-body-md5", md5Hex("{}")));
        h.put("admin-signature", s.sign("POST", "/open/x", h));
        System.out.println(s.verify("POST", "/open/x", h, 1_700_000_000_000L) + " " + s.verify("POST", "/open/x", h, 1_700_000_000_000L));  // true false
    }
}
```
**复杂度**：时间 O(h log h)（h 为头数量，实测个位数）；空间 O(13 分钟内请求数)。
**边界与坑**：
- 拼接歧义：值里含 `&` 或 `=` 时不同输入会拼出同一原文，要先规范化；body 摘要单独放头里，避免把大 body 塞进签名原文。
- nonce 表必须「先验签后烧 nonce」，否则攻击者用正确 nonce + 错误签名即可顶掉合法请求（DoS）；`removeIf` 与插入无事务，所以保留 13 分钟而非 12 分钟。
- MD5 已不适合新系统（本项目是历史契约），新接口用 HMAC-SHA256 并把 method + path 纳入 MAC；`Math.abs(now-ts)` 少了任一侧都会让未来时间戳绕过。
**面试官追问**：
1. 为什么用 `TreeMap` 不用 `HashMap`？——签名原文必须字节级可复现，`HashMap` 迭代序随实现与容量变化，跨语言客户端对不上。
2. 为什么不能只靠时间窗防重放？——12 分钟内同一个包可重放任意次，必须配 nonce 一次性，两者缺一不可。

## 题目 3：预算注水分配（KKT / water-filling）（★★★）
**场景**：优化图每轮要对同站点下 8 个活动做预算重分配，总预算固定不新增。规则：先按历史 ROAS 降序，逐档把钱填到该活动上界（`当前预算 × (1 + 单次调幅上限 20%)`），最后一档若剩余预算不够填满，则在同档活动间按容量等比例切分。要求返回每个活动的新预算，并能用 KKT 解释「为什么这么分就是最优」。
**考察点**：把业务规则翻译成带约束优化、拉格朗日乘子与互补松弛、贪心的适用条件、浮点与分位。
**参考实现**：
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public final class WaterFillingAllocator {
    static final double CAP_PCT = 0.20;      // 单次调幅上限 20%（本项目默认值）
    static final double EPS = 1e-9;
    public record Campaign(String id, double roas, double budget) {
        double cap() { return budget * (1 + CAP_PCT); }        // 上界：不越过学习期保护
    }
    public record Slice(String id, double budget, String note) {}
    public static List<Slice> allocate(List<Campaign> campaigns, double total) {
        if (campaigns == null || campaigns.isEmpty() || total <= EPS) return List.of();
        var sorted = new ArrayList<>(campaigns);
        sorted.sort(Comparator.comparingDouble(Campaign::roas).reversed().thenComparing(Campaign::id));
        int n = sorted.size();
        double[] alloc = new double[n];
        String[] note = new String[n];
        double remaining = total;
        int i = 0;
        while (i < n && remaining > EPS) {
            int j = i + 1;
            while (j < n && Math.abs(sorted.get(j).roas() - sorted.get(i).roas()) < EPS) j++;   // 同 ROAS = 同一档
            double capacity = 0;
            for (int k = i; k < j; k++) capacity += sorted.get(k).cap();
            if (remaining >= capacity) {
                for (int k = i; k < j; k++) alloc[k] = sorted.get(k).cap();   // 整档填满，继续下一档
                remaining -= capacity;
            } else {
                for (int k = i; k < j; k++) {                                 // 最后一档：按容量等比例切
                    alloc[k] = remaining * sorted.get(k).cap() / capacity;
                    note[k] = "PRORATA";
                }
                remaining = 0;
            }
            i = j;
        }
        var out = new ArrayList<Slice>(n);
        for (int k = 0; k < n; k++) out.add(new Slice(sorted.get(k).id(), Math.round(alloc[k] * 100) / 100.0, note[k] == null ? "FILLED" : note[k]));
        return List.copyOf(out);
    }
    public static void main(String[] args) {
        var cs = List.of(new Campaign("A", 2.4, 200), new Campaign("B", 1.8, 300), new Campaign("C", 1.8, 150),
                new Campaign("D", 0.9, 200), new Campaign("E", 0.9, 100));
        allocate(cs, 1000).forEach(s -> System.out.printf("%s=%.2f(%s) ", s.id(), s.budget(), s.note()));
    }   // A240 B360 C180 D146.67(PRORATA) E73.33(PRORATA)
}
```
**为什么等价于 KKT**：问题是 `max Σ roas_i·B_i`，`s.t. ΣB_i = B`，`0 ≤ B_i ≤ cap_i`；目标与约束均线性 ⇒ 凸问题，KKT 充要。拉格朗日 `L = Σroas_iB_i − λ(ΣB_i − B) + Σμ_i(B_i − cap_i) − Σν_iB_i`，一阶条件 `roas_i = λ − μ_i + ν_i`，即内点 `roas_i = λ`、触上界 `roas_i ≥ λ`、触下界 `roas_i ≤ λ`。所以最优解必然形如「按 ROAS 降序填到上界，直到某档把剩余预算用尽」，该档边际收益正好等于影子价格 λ；同档 `roas` 相同 ⇒ 边际贡献相同 ⇒ KKT 不约束比例（目标值恒定），等比例不是唯一最优，而是可复现、不偏袒的那个解。反证：若高档未满而低档分了钱，把 1 元从低档挪到高档能严格提升目标，违反最优性。
**复杂度**：时间 O(n log n)（排序主导）；空间 O(n)。
**边界与坑**：
- 总预算 > 所有上界之和时只能停在 `Σcap`，剩余预算记「无处安放」，硬塞给最后一档会越过 20% 学习期保护。
- 浮点：`remaining` 反复累减会漂，用 `EPS` 容差 + 最后按分四舍五入，并断言 `|sum − total| ≤ 0.01`；空集合/总预算 ≤0 直接返回空表，别返回全 0 让上层误判。
- 下界：本例默认 `B_i ≥ 0`；真实下界是 `b0 × (1 − 20%)`，此时贪心不再最优（要先把低 ROAS 的从下界往上顶），必须改为对 λ 二分：`B_i*(λ) = clip((w_i/2λ)², lo_i, hi_i)`。
**面试官追问**：
1. 目标函数凭什么是线性的？——真实 ROI 边际递减，本项目用 `max Σ w_i·sqrt(B_i)`（凹），边际收益 `w_i/(2√B_i)` 连续下降，解是 `B_i=(w_i/2λ)²`；线性档位版是它的分段近似，要讲清这个映射。
2. 为什么用二分而不是解析解？——解析解逐个 clip 后总预算不守恒；二分 λ 保证 `ΣB_i(λ)` 单调，60 次迭代精度足够。

## 题目 4：日累计预算变动上限校验（并发下不超发）（★★）
**场景**：安全策略要求「今日已提交变动 + 本次变动 ≤ 500.00」，单次调幅 ≤ 20%。现有实现先 `SELECT SUM(...)` 再判断，两个运营同时点执行会双双通过。要求写 `reserve(absDelta, absChangePct)`：40 个线程各申请 20.00，最终恰好 25 个成功，累计永不超过 500.00。
**考察点**：check-then-act 竞态、CAS 重试、先扣后补（reserve/release）、数据库行锁与唯一键兜底。
**参考实现**：
```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;
public final class DailyBudgetDeltaGuard {
    static final BigDecimal CAP = new BigDecimal("500.00");   // daily_budget_delta_cap 默认值
    static final BigDecimal MAX_PCT = new BigDecimal("20");   // max_single_change_pct 默认值
    private final AtomicReference<BigDecimal> used = new AtomicReference<>(BigDecimal.ZERO);
    BigDecimal reserve(BigDecimal absDelta, BigDecimal absChangePct) {
        if (absDelta == null || absDelta.signum() <= 0) throw new IllegalArgumentException("变动金额必须为正");
        if (absChangePct.compareTo(MAX_PCT) > 0) throw new IllegalStateException("调幅超上限 ±20%（防止重置学习期）");
        BigDecimal delta = absDelta.setScale(2, RoundingMode.HALF_UP);   // double 累加会出现 499.9999
        while (true) {
            BigDecimal before = used.get(), after = before.add(delta);
            if (after.compareTo(CAP) > 0) throw new IllegalStateException("今日 " + before + " + 本次 " + delta + " 超上限 " + CAP);
            if (used.compareAndSet(before, after)) return after;   // CAS 成功才叫「扣到额度」
        }
    }
    void release(BigDecimal absDelta) {   // 执行失败/回滚必须归还，否则额度泄漏
        used.updateAndGet(u -> u.subtract(absDelta).max(BigDecimal.ZERO));
    }
    BigDecimal used() { return used.get(); }
    public static void main(String[] args) throws Exception {
        var guard = new DailyBudgetDeltaGuard();
        var ok = new java.util.concurrent.atomic.AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        for (int i = 0; i < 40; i++) pool.execute(() -> {
            try { guard.reserve(new BigDecimal("20.00"), new BigDecimal("20")); ok.incrementAndGet(); }
            catch (IllegalStateException quotaExceeded) { /* 额度不足，正常丢弃 */ }
        });
        pool.shutdown(); pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("ok=" + ok.get() + " used=" + guard.used());   // 25 / 500.00
    }
}
```
**复杂度**：时间 O(1) 均摊（CAS 失败重试随竞争度，最坏 O(线程数)）；空间 O(1)。
**边界与坑**：
- 原子性只覆盖「判断+扣减」，业务动作在锁外，漏了 `release()` 就会额度泄漏——宁可少放行也不能多放行。
- 进程级 CAS 只管单实例；多实例要么 `SELECT ... FOR UPDATE` 锁策略行，要么把校验塞进条件 INSERT（`WHERE (SELECT SUM(...)) + ? <= ?`）。
- 真实兜底是约束而非校验：`optimization_ledger` 的 `ledger_key`(run:revision:EXECUTE) 主键 + `idempotency_key` 唯一索引，重复提交捕获 `DuplicateKeyException` 转 409；「今日」依赖数据库时区，跨时区部署会让上限被重复使用。
**面试官追问**：
1. 为什么不用 `synchronized`？——单实例可行但会串行化所有活动，且多实例失效；CAS 无锁、失败可退避，数据库唯一键兜底。
2. 上层已 `@Transactional` 还需要这层吗？——需要，事务只保证「要么都成功」，不保证并发两个事务互不可见（写偏斜），日累计上限必须自己保证互斥。

## 题目 5：eCPM 计算与出价封顶（★）
**场景**：BiddingAgent 要给建议出价：`eCPM = pCTR × pCVR × TargetCPA × 1000`，再乘动态倍率（ROAS 超目标 20% → ×1.30，显著低于目标或亏损 → ×0.60，略低 → ×0.85，达标 → ×1.00），基准出价 `TargetCPA × 0.65`，硬约束 `出价 ≤ TargetCPA × 0.80`。要求返回倍率、是否触发封顶和原因，供运营复核。
**考察点**：概率下限保护、舍入位置、硬约束写法（`min` 而非 `if+throw`）、把「为什么」写进返回值。
**参考实现**：
```java
import java.util.Locale;
public final class EcpmBidder {
    static final double BASE = 0.65;       // 基准出价 = 65% TargetCPA
    static final double HARD_CAP = 0.80;   // 硬约束：出价 ≤ 80% TargetCPA
    public record Bid(double baseEcpm, double multiplier, double bid, boolean capped, String reason) {}
    static Bid compute(double ctrPct, double cvrPct, double targetCpa, double roas, double targetRoas) {
        double pCtr = Math.max(0.002, ctrPct / 100.0);   // 概率下限：归零等于自动关停
        double pCvr = Math.max(0.005, cvrPct / 100.0);
        double baseEcpm = pCtr * pCvr * targetCpa * 1000.0;
        double tRoas = targetRoas <= 0 ? 1.25 : targetRoas;
        double multiplier = roas >= tRoas * 1.2 ? 1.30 : (roas < tRoas * 0.85 || roas < 1.0) ? 0.60 : roas < tRoas ? 0.85 : 1.00;
        String reason = switch ((int) Math.round(multiplier * 100)) {
            case 130 -> "ROAS 超目标 20%+ → 提价争量";
            case 60 -> "ROAS 显著低于目标或已亏损 → 降价控本";
            case 85 -> "ROAS 略低于目标 → 温和压价";
            default -> "ROAS 达标平稳 → 维持基准出价";
        };
        double rawBid = targetCpa * BASE * multiplier, cap = targetCpa * HARD_CAP;
        double bid = Math.min(rawBid, cap);     // 硬约束用 min，报错会让整轮建议失败
        return new Bid(round2(baseEcpm), multiplier, round2(bid), rawBid > cap, reason);
    }
    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    public static void main(String[] args) {
        for (var b : new Bid[]{compute(2.0, 8.0, 20.0, 2.0, 1.25), compute(2.0, 8.0, 20.0, 0.9, 1.25),
                compute(0.1, 0.1, 20.0, 2.0, 1.25)}) {   // 达标封顶 / 亏损降价 / 概率下限
            System.out.printf(Locale.US, "eCPM=%.2f x%.2f bid=%.2f capped=%s (%s)%n", b.baseEcpm(), b.multiplier(), b.bid(), b.capped(), b.reason());
        }
    }
}
```
**复杂度**：时间 O(1)；空间 O(1)。
**边界与坑**：
- `pCTR/pCVR` 为 0 或负会把 eCPM 打成 0、建议出价变 0 等于静默关停，必须夹到下限（0.002 / 0.005）。
- 舍入顺序：先算 `bid` 再回乘算 `adjustedEcpm`，若先把 bid 舍到分再回乘会有分级偏差，两次舍入都放在对外最终值上。
- 倍率区间有空档：`roas >= tRoas*1.2` 与 `roas < tRoas*0.85` 之间要确认「略低」那档确实覆盖，否则出现未定义倍率；`BASE` 若改为可配置要防 0 除。
**面试官追问**：
1. 为什么基准是 65% 而不是 100% TargetCPA？——TargetCPA 是成本上限，出价要给算法留探索空间；65% 是经验值，80% 是硬顶，两者不是一回事。
2. 为什么把公式文本放进返回体？——让运营在 UI 上复核算法，而不是只看到一个黑盒数字（`BiddingReport.formulaText`）。

## 题目 6：Top-K 亏损国家（大顶堆 vs 快速选择，含并列）（★★）
**场景**：`countryBreakdown` 返回每个国家的消耗与收入，运营要看「亏损最深的 Top-K 国家」决定是否停投。要求只看 `spend > 0` 且 `revenue < spend` 的行；第 K 名与后面并列时全部保留（不能随机砍一个）；输出按亏损从深到浅。
**考察点**：Top-K 的堆方向、并列的 dense 语义、`BigDecimal.compareTo` vs `equals`、负数「最大值」直觉陷阱。
**参考实现**：
```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
public final class TopKLossCountries {
    public record Row(String country, BigDecimal spend, BigDecimal revenue) {
        BigDecimal profit() { return revenue.subtract(spend); }   // 亏损为负
        BigDecimal roas() { return spend.signum() > 0 ? revenue.divide(spend, 4, RoundingMode.HALF_UP) : null; }
    }
    public record Loss(String country, BigDecimal profit, BigDecimal roas, boolean tie) {}
    static List<Loss> topK(List<Row> rows, int k) {
        if (rows == null || rows.isEmpty() || k <= 0) return List.of();
        // 求「最亏损 K 个」要用小顶堆：堆顶是已选中最不亏损的，新元素更亏就顶掉它
        var heap = new PriorityQueue<Row>(Comparator.comparing(Row::profit).thenComparing(Row::country));
        for (var r : rows) {
            if (!lossy(r)) continue;
            heap.offer(r);
            if (heap.size() > k) heap.poll();
        }
        if (heap.isEmpty()) return List.of();
        BigDecimal threshold = heap.peek().profit();          // 第 K 名的亏损值（负数）
        var out = new ArrayList<Loss>();
        for (var r : rows) {
            if (lossy(r) && r.profit().compareTo(threshold) <= 0) {   // <= 让并列全部保留
                out.add(new Loss(r.country(), r.profit(), r.roas(), r.profit().compareTo(threshold) == 0));
            }
        }
        out.sort(Comparator.comparing(Loss::profit).thenComparing(Loss::country));
        return List.copyOf(out);
    }
    static boolean lossy(Row r) { return r.spend().signum() > 0 && r.profit().signum() < 0; }
    public static void main(String[] args) {
        var rows = List.of(new Row("DE", new BigDecimal("800"), new BigDecimal("520")),
                new Row("BR", new BigDecimal("600"), new BigDecimal("300")), new Row("IN", new BigDecimal("500"), new BigDecimal("200")),
                new Row("US", new BigDecimal("1200"), new BigDecimal("2400")),   // 盈利，剔除
                new Row("NG", BigDecimal.ZERO, BigDecimal.ZERO));                // 无消耗，剔除
        for (var l : topK(rows, 2)) System.out.println(l.country() + " " + l.profit() + " tie=" + l.tie());
    }   // BR -300 tie / IN -300 tie
}
```
**复杂度**：时间 O(n log K)；空间 O(K)。快速选择 O(n) 平均 / O(n²) 最坏，但会打乱输入且不天然支持并列。
**边界与坑**：
- 堆方向写反（大顶堆）会把亏损最深的先弹掉，结果完全相反且不报错；`<` 与 `<=` 决定并列是否被随机丢弃，同一数据集两次跑必须同结果。
- 比较必须用 `compareTo`：`new BigDecimal("-300.0").equals(new BigDecimal("-300.00"))` 为 false，用 `equals` 会漏掉并列。
- 空表/全盈利/全无消耗三种输入都返回空表而非 null；`spend=0` 的行 ROAS 为 null 不能参与排序，否则把「无数据」误判成「最差」。
**面试官追问**：
1. 10 万行还堆吗？——仍然 O(n log K) 且可流式；快速选择要一次性载入且最坏 O(n²)。
2. 为什么不用 `sorted().limit(k)`？——全排序 O(n log n) 浪费，且并列语义还得二次扫描。

## 题目 7：逐日 ROAS 趋势——前半段 vs 后半段（★）
**场景**：`dailyReport` 最多返回 40 天逐日数据，优化图入口先算一句「日度样本 N 天（前半段 x → 后半段 y，ROAS 上升/下滑/持平）」喂给模型。要求实现 `analyze(days)`：只统计有消耗的天，按 `n/2` 切前后两段，各算聚合 ROAS（Σ收入/Σ花费）与段内线性斜率，两个信号同向才判升降。
**考察点**：比值指标不能对比值求平均、`BigDecimal.divide` 必须给 scale、奇数长度切分、空值日剔除、趋势阈值。
**参考实现**：
```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public final class DailyRoasTrend {
    static final int MAX_DAYS = 40;                          // 工具层截断 40 天
    static final BigDecimal FLAT_EPS = new BigDecimal("0.02");
    public record Day(LocalDate date, BigDecimal spend, BigDecimal revenue) {}
    public record Trend(BigDecimal early, BigDecimal late, double eSlope, double lSlope, String verdict) {}
    static Trend analyze(List<Day> days) {
        if (days == null || days.isEmpty()) return new Trend(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, "无样本");
        var active = new ArrayList<Day>();
        for (var d : days) {                                 // spend=0 的日子 ROAS 无定义，混进来会拉平趋势
            if (d.spend().signum() > 0) active.add(d);
            if (active.size() >= MAX_DAYS) break;
        }
        int n = active.size();
        if (n < 2) return new Trend(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, "样本不足");
        int half = n / 2;                                    // 奇数时前半段少一天，与 summarizeDailyTrend 一致
        BigDecimal early = aggregate(active, 0, half), late = aggregate(active, half, n);
        var series = active.stream().map(d -> d.revenue().divide(d.spend(), 4, RoundingMode.HALF_UP).doubleValue()).toList();
        double eSlope = slope(series, 0, half), lSlope = slope(series, half, n);
        BigDecimal delta = late.subtract(early);
        String verdict = delta.compareTo(FLAT_EPS) > 0 && lSlope > 0 ? "上升" : delta.compareTo(FLAT_EPS.negate()) < 0 && lSlope < 0 ? "下滑" : "持平";
        return new Trend(early, late, eSlope, lSlope, verdict);
    }
    static BigDecimal aggregate(List<Day> days, int from, int to) {
        BigDecimal spend = BigDecimal.ZERO, revenue = BigDecimal.ZERO;
        for (int i = from; i < to; i++) { spend = spend.add(days.get(i).spend()); revenue = revenue.add(days.get(i).revenue()); }
        return spend.signum() > 0 ? revenue.divide(spend, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;   // Σ收入/Σ花费
    }
    static double slope(List<Double> ys, int from, int to) {  // 段内最小二乘，x 用段内序号避免量纲不一致
        int m = to - from;
        if (m < 2) return 0.0;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < m; i++) { double x = i, y = ys.get(from + i); sx += x; sy += y; sxy += x * y; sxx += x * x; }
        double denom = m * sxx - sx * sx;
        return Math.abs(denom) < 1e-12 ? 0.0 : (m * sxy - sx * sy) / denom;
    }
    public static void main(String[] args) {
        var days = new ArrayList<Day>();
        for (int i = 0; i < 14; i++) days.add(new Day(LocalDate.of(2026, 11, 1).plusDays(i),
                new BigDecimal("100.00"), new BigDecimal(150 - i * 5)));   // 逐日走低：日 ROAS 1.50 → 0.85
        var t = analyze(days);
        System.out.printf("%s→%s slope=%.4f/%.4f verdict=%s%n", t.early(), t.late(), t.eSlope(), t.lSlope(), t.verdict());
    }
}
```
**复杂度**：时间 O(n)，n ≤ 40；空间 O(n)。
**边界与坑**：
- 比值陷阱：每天 ROAS 求算术平均 ≠ `Σ收入/Σ花费`，花费差异大时严重失真，聚合口径必须是后者。
- `divide` 不给 scale 会抛 `ArithmeticException`（10/3 非终止小数），必须显式 `scale + RoundingMode`；日期用 `LocalDate` 而非 `Instant`，否则跨时区会「同一天算两次/漏一天」。
- 只有 2 天时两段各 1 天，斜率算不出返回 0，verdict 只能是持平；`0.02` 这类平盘阈值要写进文档，否则「1.5000→1.5001」被判上升。
**面试官追问**：
1. 为什么不用全段斜率？——全段斜率对「先跌后涨」给出 0，掩盖形态；分段对比更贴近「最近是不是变差了」。
2. 为什么要求斜率同向？——防止单日大促把均值拉高而整体仍衰减，两个信号互相印证降误报。

## 题目 8：检查点幂等写入（MERGE / upsert + 唯一键）（★★）
**场景**：LangGraph4j 的 `JdbcCheckpointSaver.put` 会因重试、节点重放被同一 `(threadId, checkpointId)` 调用多次。要求：同一复合键只保留一行、后写覆盖前写、重复执行不产生脏数据，且 `latest(threadId)` 总能取到最近写入的检查点（服务重启要能续跑）。
**考察点**：upsert 语义（MERGE / ON DUPLICATE KEY / ON CONFLICT）、复合键拼接、幂等 vs 覆盖、「最新」的判定、序列化降级。
**参考实现**：
```java
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public final class IdempotentCheckpointStore {
    /** 真实 SQL（H2）：MERGE ... KEY 天然幂等；MySQL 用 ON DUPLICATE KEY UPDATE，PG 用 ON CONFLICT DO UPDATE。 */
    static final String MERGE_SQL = """
            MERGE INTO agent_checkpoint (thread_id, checkpoint_id, node_id, next_node_id, state_json, created_at) KEY (thread_id, checkpoint_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""";
    public record Cp(String threadId, String checkpointId, String nodeId, String next, String state) {}
    private record Entry(Cp cp, long seq) {}
    private final Map<String, Entry> rows = new HashMap<>();
    private long seq = 0;
    void put(Cp cp) {   // 重复 put 只覆盖不新增
        String thread = cp.threadId() == null || cp.threadId().isBlank() ? "run-unknown" : cp.threadId();
        String id = cp.checkpointId() == null || cp.checkpointId().isBlank() ? UUID.randomUUID().toString() : cp.checkpointId();
        String node = cp.nodeId() == null || cp.nodeId().isBlank() ? "__START__" : cp.nodeId();   // 缺省节点
        var normalized = new Cp(thread, id, node, cp.next(), cp.state() == null || cp.state().isBlank() ? "{}" : cp.state());
        rows.merge(thread + '\u0000' + id, new Entry(normalized, ++seq), (old, incoming) -> incoming);
    }
    Cp latest(String threadId) {   // 等价 ORDER BY created_at DESC, checkpoint_id DESC LIMIT 1
        Entry best = null;
        for (var e : rows.values()) if (e.cp().threadId().equals(threadId) && (best == null || e.seq() > best.seq())) best = e;
        return best == null ? null : best.cp();
    }
    public static void main(String[] args) {
        var store = new IdempotentCheckpointStore();
        String thread = "run-7f3a";   // threadId = "run-" + runId
        store.put(new Cp(thread, "cp-01", "intake_context", "strategy_planner", "{\"step\":1}"));
        store.put(new Cp(thread, "cp-01", "intake_context", "strategy_planner", "{\"step\":1}"));   // 重放
        store.put(new Cp(thread, "cp-01", "intake_context", "guardrail_critic", "{\"step\":2}"));   // 覆盖
        store.put(new Cp(thread, "cp-02", "guardrail_critic", "finalize_plan", "{\"step\":3}"));
        System.out.println("rows=" + store.rows.size() + " latest=" + store.latest(thread).checkpointId());  // 2 / cp-02
    }
}
```
**复杂度**：时间 O(1)（SQL 版为唯一索引点查/点写）；空间 O(threads × checkpoints)。
**边界与坑**：
- 「幂等」和「覆盖」语义不同：同一 `checkpointId` 重放必须覆盖（否则续跑读到旧状态），但同一笔执行必须拒绝（唯一键 + `DuplicateKeyException`），一个开关不能兼管两件事。
- 复合键别用 `":"` 拼：`a|b`+`c` 与 `a`+`b|c` 会撞键，要么用 `\u0000`，要么直接建联合主键；「最新」用自增 `seq` 判定，时间戳同秒时会退化成字符串序。
- state 有不可 JSON 化的对象时不能抛出去阻塞图执行：逐字段 `String.valueOf` 兜底，再失败写 `{}` 并告警；工具原始输出要截断（本项目 6500 字符），否则检查点表先于业务表爆。
**面试官追问**：
1. 为什么不先 `SELECT` 再 `INSERT/UPDATE`？——两次往返 + 中间有竞态窗口，单条 MERGE 由数据库保证原子。
2. 内存 Map 不行吗？——重启就续不上，人工审批也追不到「当时图停在哪」，所以必须落库。

## 题目 9：SSE 事件总线（订阅 / 回放 / 剔除 / 零开销）（★★★）
**场景**：智能体跑一轮几十秒，前端允许中途才打开进度页。要求按 `runId` 维护订阅者：迟到订阅先收到已发生的节点事件（回放）再收实时事件；推送失败自动剔除；没有订阅者时发布路径不建对象、不加锁；结束发 `done` 并清理该 `runId` 的所有引用，避免 emitter 泄漏。
**考察点**：`ConcurrentHashMap` + `CopyOnWriteArrayList` 的读写权衡、回放与注册的原子性、遍历中删除、内存上限、锁外发送。
**参考实现**：
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
public final class RunEventBus {
    @FunctionalInterface interface Subscriber { void onEvent(String name, Object payload) throws Exception; }
    static final long TIMEOUT_MS = 180_000L; private static final int MAX_REPLAY = 500;   // SseEmitter 180s；回放上限防撑爆内存
    private record Event(String name, Object payload) {}
    private static final class Channel {
        final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();   // 读多写少
        final List<Event> history = new ArrayList<>();                      // 由 this 锁保护
    }
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    void subscribe(String runId, Subscriber s) {
        Channel ch = channels.computeIfAbsent(runId, ignored -> new Channel());
        synchronized (ch) {                  // 回放与注册同一临界区，否则中间产生的事件会丢
            for (Event e : ch.history) try { s.onEvent(e.name(), e.payload()); } catch (Exception dead) { return; }
            ch.subscribers.add(s);
        }
    }
    void publish(String runId, String name, Object payload) {
        Channel ch = channels.get(runId);
        if (ch == null) return;              // 无订阅者：不建对象、不加锁、直接返回
        List<Subscriber> targets;
        synchronized (ch) {
            if (ch.subscribers.isEmpty()) return;
            ch.history.add(new Event(name, payload));
            if (ch.history.size() > MAX_REPLAY) ch.history.remove(0);
            targets = new ArrayList<>(ch.subscribers);   // 快照，发送放到锁外
        }
        for (Subscriber s : targets) {
            try { s.onEvent(name, payload); } catch (Exception dead) { ch.subscribers.remove(s); }   // 自动剔除
        }
    }
    void complete(String runId, String status) {
        publish(runId, "done", Map.of("type", "done", "runId", runId, "status", status));
        Channel ch = channels.remove(runId);   // 整 key 移除，避免 emitter 泄漏
        if (ch != null) synchronized (ch) { ch.subscribers.clear(); ch.history.clear(); }
    }
    int subscribers(String runId) { Channel ch = channels.get(runId); return ch == null ? 0 : ch.subscribers.size(); }
    public static void main(String[] args) {
        var bus = new RunEventBus();
        var live = new ArrayList<String>();
        var late = new ArrayList<String>();
        bus.subscribe("run-1", (n, p) -> live.add(n));
        bus.publish("run-1", "node", "intake_context");
        bus.subscribe("run-1", (n, p) -> late.add(n));      // 迟到：先收 1 条回放
        bus.publish("run-1", "node", "guardrail_critic");
        bus.subscribe("run-1", (n, p) -> { throw new IllegalStateException("client gone"); });
        bus.publish("run-1", "node", "finalize_plan");      // 坏订阅者被剔除，其余照收
        bus.publish("run-any", "node", "no-op");            // 无订阅：零开销
        bus.complete("run-1", "DONE");
        System.out.println("live=" + live.size() + " late=" + late.size() + " subs=" + bus.subscribers("run-1"));
    }   // 4 4 / 0
}
```
**复杂度**：发布 O(s)（s 为订阅者数）；订阅 O(h)（h ≤ 500）；空间 O(runs × (h + s))。
**边界与坑**：
- 回放竞态：先回放再注册会漏「注册前」的事件，先注册再回放会重复；必须放进同一临界区，发送则在锁外做，否则一个慢客户端拖住整轮图。
- 遍历中删除：`CopyOnWriteArrayList` 迭代器是快照，删元素安全；换成 `ArrayList` 必 `ConcurrentModificationException`。
- 泄漏：`onCompletion/onTimeout/onError` 三个回调都要挂钩移除，只挂一个时浏览器直接关标签页会留下僵尸 emitter 到 180s 超时；`history` 不设上限，几千节点的 run 会把 token 明细全留在堆里。
**面试官追问**：
1. 为什么不用 WebSocket？——进度是单向 server→client，SSE 走普通 HTTP、自带重连、网关穿透好，代价是只能单向。
2. 「零开销」省了什么？——无订阅时不 `new Event`、不 `new ArrayList`、不进 `synchronized`；8 节点 × N 轮的热路径上这点分配会放大成 GC 压力。

## 题目 10：CTR 双样本比例检验 + Bonferroni 校正（★★★）
**场景**：创意工坊要判「B 组 CTR 是否真比 A 组高」，一次实验可能有 B/C/D 多个实验组。要求对每个实验组做双样本比例 z 检验，用 Bonferroni 校正（α/m），样本不足或观测周期不一致时一律不判胜，返回 z、p、校正后 p 与结论。
**考察点**：合并方差、双侧 p 值、多重比较膨胀、样本量门槛、单双侧取舍、「不判胜」的产品语义。
**参考实现**：
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public final class CtrSignificance {
    static final double ALPHA = 0.05;
    static final int MIN = 10;   // 每臂点击/未点击各 ≥10 才允许正态近似
    public record Arm(String name, long impressions, long clicks) {}
    public record Verdict(String variant, double ctr1, double ctr2, double z, double p, double adjusted,
                          int comparisons, boolean enough, boolean wins, String label) {}
    static List<Verdict> evaluate(Arm control, List<Arm> variants, boolean samePeriod) {
        if (control == null || variants == null || variants.isEmpty()) return List.of();
        int m = variants.size();          // Bonferroni：m = 比较次数，阈值收紧到 α/m
        var out = new ArrayList<Verdict>(m);
        for (var v : variants) {
            double n1 = control.impressions(), n2 = v.impressions(), k1 = control.clicks(), k2 = v.clicks();
            double ctr1 = n1 > 0 ? k1 / n1 : 0, ctr2 = n2 > 0 ? k2 / n2 : 0;
            double pool = (n1 + n2) > 0 ? (k1 + k2) / (n1 + n2) : 0;                        // 合并比例 p̂
            double se = n1 > 0 && n2 > 0 ? Math.sqrt(pool * (1 - pool) * (1 / n1 + 1 / n2)) : 0;
            double z = se > 0 ? (ctr2 - ctr1) / se : 0;                                     // 保留符号
            double p = se > 0 ? Math.min(1, 2 * (1 - normalCdf(Math.abs(z)))) : 1;           // 双侧 p
            double adjusted = Math.min(1, p * m);                                            // p×m ≡ α/m
            boolean enough = k1 >= MIN && k2 >= MIN && (n1 - k1) >= MIN && (n2 - k2) >= MIN;
            boolean wins = enough && samePeriod && adjusted < ALPHA && ctr2 > ctr1;
            String label = !samePeriod ? "周期不一致，不判胜" : !enough ? "样本不足，不判胜" : wins ? "校正后显著胜出" : "未证实 CTR 提升";
            out.add(new Verdict(v.name(), round4(ctr1), round4(ctr2), round4(z),
                    round4(p), round4(adjusted), m, enough, wins, label));
        }
        return List.copyOf(out);
    }
    static double normalCdf(double z) {   // Abramowitz-Stegun 近似，误差 < 1e-7
        double t = 1 / (1 + 0.2316419 * z), d = 0.3989422804014327 * Math.exp(-z * z / 2);
        return 1 - d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
    }
    static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
    public static void main(String[] args) {
        for (var v : evaluate(new Arm("A", 20_000, 400), List.of(new Arm("B", 20_000, 480), new Arm("C", 20_000, 460),
                new Arm("D", 20_000, 8)), true)) {   // B 显著 / C 校正后不显著 / D 样本不足
            System.out.printf(Locale.US, "%s z=%.3f p=%.4f adj=%.4f win=%s (%s)%n", v.variant(), v.z(), v.p(), v.adjusted(), v.wins(), v.label());
        }
    }
}
```
**公式**：`p̂ = (k₁+k₂)/(n₁+n₂)`，`SE = √(p̂(1−p̂)(1/n₁+1/n₂))`，`z = (ĉtr₂−ĉtr₁)/SE`，双侧 `p = 2(1−Φ(|z|))`；判定 `p < α/m`（等价 `p·m < α`），α = 0.05，m=1 临界 `|z| ≥ 1.96`，m=5 临界 `2.58`。
**复杂度**：时间 O(m)；空间 O(m)。
**边界与坑**：
- 多重比较：3 组同时比，不校正时「至少一个假阳性」约 `1−0.95³ ≈ 14%`；校正后阈值 `0.05/3 ≈ 0.0167`，临界 `|z| ≈ 2.39`。
- 样本门槛：n=20000 时 1 次点击差异也会「显著」，正态近似在稀疏计数下不可靠——本项目要求每臂点击与未点击各 ≥10、总曝光目标 20000，且看到显著就停会大幅抬高假阳性（peeking）。
- 双侧 vs 单侧：业务问「是否更高」，但双侧更保守（p 是单侧两倍），选单侧必须写死；周期不一致（A 跑 7 天、B 跑 3 天）要直接不判胜，不能先算个 p 再说。
**面试官追问**：
1. 为什么用合并方差？——原假设两组比例相同，此时最佳估计是把数据合并成一个 `p̂`；备择假设下差异大时略保守，样本够时几乎无差别。
2. p=0.03 就一定赢吗？——p 只说「H₀ 下出现该差异的概率」，还要看效应量（CTR 提升 0.3% 值不值得换素材）和转化回收，所以结论写「请结合转化回收人工决策」。

## 使用建议（面试官侧）
时间分配：★ 5 分钟、★★ 8-10 分钟、★★★ 15 分钟；一场 1-2 题即可，优先从第 3、9、10 题挑。代码零依赖，不要让候选人现场起 Spring/MyBatis；卡住时按「边界 → 复杂度 → 换算法」给提示，能顺提示补完的按原题 70% 计分。
