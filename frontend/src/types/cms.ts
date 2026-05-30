export type RouteView =
  | "overview"
  | "campaigns"
  | "reports"
  | "platform-reference"
  | "rules"
  | "content"
  | "accounts"
  | "settings"
  | "reportconfig"
  | "dictionaries"
  | "feedback"
  | "agent"
  | "vault"
  | "dimensions"
  | "system"
  | "profile";

export type AvatarColor = "teal" | "indigo" | "amber" | "rose" | "violet";

export interface BoundAdAccount {
  id: number;
  name: string;
  accountId: string;
  platform: string;
  partner: string;
  operator: string;
}

export interface UserAccount {
  id?: number;
  name: string;
  root?: number;
  partner?: string;
  status?: number;
  displayName?: string;
  email?: string;
  phone?: string;
  department?: string;
  jobTitle?: string;
  timezone?: string;
  avatarColor?: AvatarColor | string;
  desc?: string;
  autoArticle?: boolean;
  autoCampaign?: boolean;
  showMaterial?: boolean;
  siteList?: string[];
  activeSiteCount?: number;
  boundAdAccounts?: BoundAdAccount[];
  managedAccountCount?: number;
  managedCampaignCount?: number;
  recentOptimizationCount?: number;
  lastLoginAt?: string;
  ctime?: string;
  utime?: string;
}

export interface BootstrapContext {
  mode?: "cms" | "simulation";
  localCms?: boolean;
  requireStaffLogin?: boolean;
  hasArchiveData?: boolean;
  hasLogReport?: boolean;
  campaigns?: unknown[];
  exampleRules?: unknown;
  exampleChannel?: unknown;
  account?: UserAccount | null;
  archiveData?: Record<string, unknown[]> | null;
  cmsUnavailable?: string;
}

export interface NavItem {
  id: RouteView;
  label: string;
  icon: string;
}

export interface NavSection {
  group: string;
  items: NavItem[];
}

export const ROUTE_TITLES: Record<RouteView, string> = {
  overview: "运营总览",
  campaigns: "广告活动",
  reports: "投放报表",
  "platform-reference": "海外平台投放参考",
  rules: "投放规则",
  content: "内容管理",
  accounts: "账号与渠道",
  settings: "站点配置",
  reportconfig: "报表配置",
  dictionaries: "基础资料",
  feedback: "用户反馈",
  agent: "智能体工作台",
  vault: "渠道密钥",
  dimensions: "多维报表",
  system: "系统运维",
  profile: "个人资料"
};

export const NAV_SECTIONS: NavSection[] = [
  {
    group: "投放运营",
    items: [
      { id: "overview", label: "运营总览", icon: "▦" },
      { id: "campaigns", label: "广告活动", icon: "▤" },
      { id: "reports", label: "投放报表", icon: "▥" },
      { id: "rules", label: "投放规则", icon: "≋" }
    ]
  },
  {
    group: "智能优化",
    items: [
      { id: "agent", label: "智能体工作台", icon: "✦" }
    ]
  },
  {
    group: "资产与渠道",
    items: [
      { id: "content", label: "内容管理", icon: "▧" },
      { id: "accounts", label: "账号与渠道", icon: "◎" },
      { id: "vault", label: "渠道密钥", icon: "◈" },
      { id: "settings", label: "站点配置", icon: "⚙" }
    ]
  },
  {
    group: "系统与个人",
    items: [
      { id: "dictionaries", label: "基础资料", icon: "☷" },
      { id: "feedback", label: "用户反馈", icon: "≡" },
      { id: "system", label: "系统运维", icon: "◉" },
      { id: "profile", label: "个人资料", icon: "❖" }
    ]
  }
];

declare global {
  interface Window {
    md5: (input: string) => string;
  }
}
