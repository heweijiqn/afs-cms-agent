import { adPlatforms } from "./form-choices.js";

const field = (key: string, title: string, kind: string = "text", required: boolean = false, options: any = undefined) => ({ key, title, kind, required, options });
const must = (key: string, title: string, kind: string = "text", options?: any) => field(key, title, kind, true, options);
const f = field;
const choose = (key: string, title: string, values: any) => must(key, title, "select", values);
const p = (id: string, title: string, path: string, fields: any[] = [], extra: any = {}) => ({ id, title, path, fields, method: "POST", ...extra });
const g = (id: string, title: string, path: string, fields: any[] = [], extra: any = {}) => ({ id, title, path, fields, method: "GET", ...extra });
const root = { root: true };
const risk = { confirm: true };
const rootRisk = { ...root, ...risk };
const pages = [must("pageIndex", "页码", "positive"), must("pageSize", "每页条数", "positive")];
const defaults = { pageIndex: 1, pageSize: 10 };
const ad = must("adResourceId", "广告活动 ID");
const article = must("articleId", "文章 ID");
const id = must("id", "记录 ID", "positive");
const site = must("site", "站点");
// AdSense 渠道管理仍使用渠道 ID；审核活动不再要求填写渠道 ID。
const channel = must("channelId", "渠道 ID");
const pub = must("pubId", "AdSense 发布商 ID");
const reportChoice = (key, title, kind = "text", required = false) => ({ ...f(key, title, kind, required), lookup: true });

export const localMaintenanceOperations = [
  g("adsense-source-report", "AdSense 明细", "/cms/console/maintenance/adsense", [
    choose("reportChannel", "渠道 ID", []), must("dateStart", "开始日期", "date"), must("dateEnd", "结束日期", "date"),
    f("countryCode", "国家 / 地区", "select", false, [])
  ], { ...root, maintenance: true }),
  p("campaign-recalculate", "按日期重算活动数据", "/cms/console/maintenance/recalculate",
    [choose("date", "报表日期", [])], { ...rootRisk, maintenance: true, submitText: "执行重算" }),
  g("cms-daily-report", "生成投放日报", "/daily/report", [choose("date", "报表日期", [])],
    { ...root, maintenance: true, submitText: "生成日报" })
];

const tiktok = [
  must("campaign", "活动名称"), must("adAccountList", "广告账户 ID（每行一项）", "list"),
  must("accountId", "Pixel ID 或代码"), must("isSmartCreative", "智能创意", "boolean"), f("textCount", "智能创意文案数量", "positive"),
  choose("optimizationGoal", "优化目标", [["CLICK", "点击"], ["TRAFFIC_LANDING_PAGE_VIEW", "落地页浏览"], ["CONVERT", "转化"]]),
  choose("optimizationEvent", "转化事件", [["SHOPPING", "购买"], ["ADD_TO_CART", "加入购物车"], ["COMPLETE_PAYMENT", "完成付款"], ["VIEW_CONTENT", "查看内容"], ["REGISTRATION", "注册"]]),
  choose("bidPriceType", "出价类型", [["BID_TYPE_CUSTOM", "手动出价"], ["BID_TYPE_NO_BID", "自动出价"]]), f("bidPriceValue", "出价值", "number"), must("country", "投放国家"),
  must("placementList", "版位", "list"), choose("campaignBudgetModel", "活动预算模式", [["BUDGET_MODE_INFINITE", "不限"], ["BUDGET_MODE_DAY", "日预算"], ["BUDGET_MODE_TOTAL", "总预算"]]),
  f("campaignBudget", "活动预算", "number"), choose("adGroupBudgetModel", "广告组预算模式", [["BUDGET_MODE_DAY", "日预算"], ["BUDGET_MODE_TOTAL", "总预算"]]),
  must("adGroupBudget", "广告组预算", "number"), must("textList", "广告文案（每行一项）", "list"),
  must("materialList", "素材列表（JSON 数组）", "objects", [{ adMaterialId: "", name: "", type: "VIDEO", url: "" }]),
  must("campaignCountEachAccount", "每账号活动数", "positive"), must("adCountEachCampaign", "每活动广告数", "positive")
];

const writing = [
  must("title", "标题"), must("lang", "语言代码"), must("category", "分类标识"),
  must("textList", "正文（空行分段）", "jsonListText"), f("firstP", "首段", "textarea"),
  f("imgUrl", "封面地址"), f("terms", "关键词"), f("insideShow", "站内展示", "select", false, [["1", "展示"], ["0", "隐藏"]])
];

const material = [
  must("name", "素材名称"), choose("type", "素材类型", [["IMAGE", "图片"], ["VIDEO", "视频"]]), must("lang", "语言"),
  must("countryList", "国家代码（每行一项）", "list"), must("category", "素材分类（每行一项）", "list"),
  must("template", "素材模板"), must("url", "媒体地址"), must("size", "文件大小（字节）", "integer"),
  f("cover", "封面地址"), f("dimension", "分辨率"), f("ratio", "比例"), f("duration", "时长（秒）", "integer")
];

const rule = [
  must("name", "规则名称"), choose("type", "规则类型", [["REPEAT", "重复规则"], ["CONT", "连续规则"]]),
  choose("actionCycle", "执行周期", [["REPEAT", "重复执行"], ["CONT", "连续执行"]]),
  choose("actionTime", "执行时段", Array.from({ length: 24 }, (_, hour) => [String(hour), `${String(hour).padStart(2, "0")}:00`])),
  f("budgetChange", "预算调整上限（%）", "number")
];

const category = [must("value", "父分类代码"), must("label", "父分类名称"),
  must("child.value", "子分类代码"), must("child.label", "子分类名称")];
const lang = [must("value", "语言代码"), must("label", "语言名称")];
const operators = [must("name", "后台账号"), must("desc", "说明"),
  f("siteList", "允许的站点（每行一项，留空表示清空）", "list"),
  f("adAccountList", "允许的广告账号数据库 ID（每行一项，留空表示清空）", "integers"),
  must("autoArticle", "文章自动审核权限", "boolean"), must("autoCampaign", "活动自动审核权限", "boolean"),
  must("showMaterial", "显示素材", "boolean")];

export const operations = {
  campaigns: [
    p("campaign-new", "创建广告活动", "/cms/ad/resource/add", [article, must("campaign", "活动名称"), must("title", "广告标题"),
      choose("adPlatform", "投放平台", adPlatforms.filter(platform => platform !== "TikTok")), f("adAccount", "广告账号"),
      f("am", "负责人"), f("country", "国家或地区"), f("keyword", "关键词")], { ...risk, split: ["adPlatform"] }),
    p("tiktok-new", "创建 TikTok 活动", "/cms/ad/resource/tiktok/add", [article, must("title", "广告标题"), ...tiktok], risk),
    p("campaign-edit", "编辑活动", "/cms/ad/resource/edit", [ad, must("campaign", "活动名称"), f("keyword", "关键词"), f("country", "国家")], risk),
    p("tiktok-edit", "编辑 TikTok 活动", "/cms/ad/resource/tiktok/edit", [ad, ...tiktok], risk),
    p("campaign-audit", "审核活动", "/cms/ad/resource/audit", [ad, choose("state", "审核结果", [["PASS", "通过"], ["REJECT", "驳回"]]),
      f("channelId", "通过时填写渠道 ID"), f("landPageUrl", "通过时填写落地页 URL"), f("msg", "审核说明", "textarea")], risk),
    p("campaign-publish", "在广告平台创建 Campaign", "/cms/console/platform-campaign",
      [ad, must("advertiser", "平台广告账号"), must("credentialAlias", "已保存的渠道凭据")], rootRisk),
    p("campaign-bind", "绑定规则", "/cms/ad/resource/rule/bind", [ad, must("ruleId", "投放规则 ID", "positive")], rootRisk),
    p("campaign-unbind", "解绑规则", "/cms/ad/resource/rule/unbind", [ad], rootRisk),
    p("campaign-archive", "归档活动", "/cms/ad/resource/archive", [ad], risk),
    p("campaign-delete", "删除活动", "/cms/ad/resource/del", [ad], risk),
    g("campaign-info", "查询活动详情", "/cms/ad/resource/search/info", [ad]),
    g("campaign-group", "查询平台广告组", "/cms/ad/resource/platform/adg/list", [ad]),
    g("campaign-lookup", "按名称查询活动", "/cms/ad/resource/search/info/campaign", [must("campaign", "活动名称")]),
    g("campaign-rule-logs", "查看规则执行日志", "/cms/ad/resource/rule/log", [ad])
  ],
  articles: [
    p("article-new", "新建文章", "/cms/article/add", writing, { numbers: ["insideShow"] }),
    p("article-edit", "编辑文章", "/cms/article/edit", [article, ...writing, f("site", "站点")], { numbers: ["insideShow"] }),
    p("article-audit", "文章审核", "/cms/article/audit", [article, choose("state", "审核结果", [["PASS", "通过"], ["REJECT", "驳回"]]), f("site", "通过时发布到站点"), f("msg", "审核说明", "textarea")], risk),
    p("article-on-off", "文章上线或下线", "/cms/article/update/onOff", [article, choose("onOff", "目标状态", [["1", "上线"], ["0", "下线"]])], { ...risk, numbers: ["onOff"] }),
    p("article-cancel", "撤回文章更新", "/cms/article/cancel/update", [article], risk),
    p("article-archive", "归档文章", "/cms/article/archive", [article], risk),
    p("article-delete", "删除文章", "/cms/article/del", [article], risk),
    g("article-info", "查询文章详情", "/cms/article/search/info", [article]),
    g("article-keywords", "查询文章关键词数据", "/cms/article/keyword/gaData/list", [article]),
    p("article-cache", "清除站点缓存", "/cms/article/cache/clear", [site, choose("type", "清除方式", [["files", "按文件 URL"], ["prefixes", "按路径前缀"], ["tags", "按缓存标签"], ["hosts", "按域名"]]),
      must("data", "清除目标（每行一项）", "list")], rootRisk)
  ],
  materials: [
    p("material-new", "新建素材", "/cms/ad/material/add", material, { ...root, batch: true }),
    p("material-edit", "编辑素材", "/cms/ad/material/edit", [id, ...material], root),
    p("material-delete", "删除素材", "/cms/ad/material/del", [must("idList", "素材记录 ID（每行一项）", "integers")], rootRisk),
    p("upload-image", "上传图片", "/cms/upload/image", [must("file", "图片", "image")], { ...risk, multipart: true }),
    p("upload-file", "上传文件", "/cms/ali/upload/file", [must("file", "文件", "file")], { ...risk, multipart: true }),
    p("generate-image", "AI 生成图片", "/cms/ai/image/create", [must("desc", "图片描述", "textarea")], risk)
  ],
  copy: [
    p("copy-new", "新建广告文案", "/cms/ad/text/console/add", [must("name", "文案组"), must("textList", "文案（每行一项）", "list")], root),
    p("copy-edit", "编辑广告文案", "/cms/ad/text/console/edit", [id, must("name", "文案组"), must("textList", "文案（每行一项）", "list")], root),
    p("copy-delete", "删除广告文案", "/cms/ad/text/del", [id], rootRisk)
  ],
  categories: [
    p("category-new", "维护文章分类", "/cms/category/add", category, root),
    g("material-category-list", "查询素材分类", "/cms/ad/material/category/list"),
    p("material-category-new", "维护素材分类", "/cms/ad/material/category/add", category, root)
  ],
  publishers: [
    p("publisher-new", "新增 AdSense 账号", "/cms/adsense/account/add", [pub, must("siteList", "绑定站点（每行一项）", "list")], root),
    p("publisher-edit", "编辑 AdSense 账号", "/cms/adsense/account/edit", [pub, must("siteList", "绑定站点（每行一项）", "list")], root),
    g("publisher-ids", "查询发布商 ID 列表", "/cms/adsense/account/pubId/list")
  ],
  channels: [
    p("channel-new", "新增 AdSense 渠道", "/cms/adsense/channel/add", [channel, pub], { ...root, batch: true }),
    p("channel-delete", "删除 AdSense 渠道", "/cms/adsense/channel/delete", [channel, pub], rootRisk)
  ],
  platforms: [
    p("platform-new", "新增平台广告账号", "/cms/ad/account/info/add", [must("name", "账号名称"), must("accountId", "平台账号 ID"),
      choose("adPlatform", "广告平台", adPlatforms), f("clientId", "客户端 ID"), f("clientSecret", "客户端密钥", "secret")], rootRisk)
  ],
  operators: [
    p("operator-new", "新增操作员", "/cms/user/account/console/add", operators, { ...root, reveal: true }),
    p("operator-edit", "编辑操作员", "/cms/user/account/edit", operators, rootRisk),
    g("operator-details", "查询操作员详情", "/cms/console/operator", [must("name", "后台账号")], root),
    g("operator-partners", "查询合作方账号", "/cms/user/list", [], root)
  ],
  rules: [
    p("rule-new", "新建投放规则", "/cms/ad/resource/rule/add", rule, { ...root, numbers: ["actionTime"] }),
    p("rule-edit", "编辑投放规则", "/cms/ad/resource/rule/edit", [id, ...rule], { ...root, numbers: ["actionTime"] }),
    p("rule-delete", "删除投放规则", "/cms/ad/resource/rule/del", [id], rootRisk)
  ],
  reports: [
    p("report-dimension", "多维投放报表", "/cms/ad/data/dimension/list", [reportChoice("pubId", "AdSense 发布商 ID", "text", true),
      must("dateStart", "开始日期", "date"), must("dateEnd", "结束日期", "date"),
      choose("dimensionList", "统计维度", [
        ["date", "按日期"], ["date,country", "按日期 + 国家"], ["country", "按国家"],
        ["campaignId", "按活动"], ["date,campaignId", "按日期 + 活动"],
        ["country,campaignId", "按国家 + 活动"], ["date,country,campaignId", "按日期 + 国家 + 活动"]
      ]),
      reportChoice("campaignId", "广告活动（可多选）", "list"), reportChoice("campaign", "活动名称"),
      reportChoice("channelId", "渠道"), reportChoice("adPlatform", "平台"), reportChoice("partner", "操作员"),
      reportChoice("country", "国家（可多选）", "list"), reportChoice("articleId", "文章 ID（可多选）", "list"),
      reportChoice("am", "负责人（可多选）", "list")],
    { read: true, split: ["dimensionList"], defaults: { dimensionList: "date" } })
  ],
  dictionaries: [
    g("language-list", "语言列表", "/cms/lang/list"),
    p("language-new", "新增语言", "/cms/lang/add", lang, root),
    p("language-edit", "编辑语言", "/cms/lang/edit", [id, ...lang], root),
    g("country-list", "国家和地区", "/cms/region/list"),
    g("site-list", "站点列表", "/cms/site/list"),
    g("manager-list", "投放负责人", "/cms/am/list"),
    g("strategy-list", "文章处理策略", "/cms/strategy/list"),
    g("directory-list", "栏目目录", "/cms/dir/list")
  ],
  system: [
    g("cms-health", "CMS 健康检查", "/health", [], root),
    p("cms-health-post", "CMS POST 健康检查", "/healthX", [], root),
    g("adsense-source-report", "AdSense 明细", "/open/adsense/data/list",
      [channel, must("dateStart", "开始日期", "date"), must("dateEnd", "结束日期", "date"),
        f("countryCode", "国家 / 地区"), f("authorization", "接口访问令牌", "secret")], root),
    g("campaign-recalculate", "重算昨日活动数据", "/open/ad/resource/do/last-day", [], rootRisk),
    g("cms-daily-report", "生成投放日报", "/daily/report", [], rootRisk)
  ],
  feedback: [
    p("feedback-list", "查询用户反馈", "/cms/feedback/list", [f("site", "站点"), ...pages], { read: true, defaults })
  ]
};

