import React, { useCallback, useEffect, useState } from "react";
import { AvatarColor, BootstrapContext, UserAccount } from "../types/cms";
import { accountName, request, setCredentials } from "../modules/api";
import { toast } from "../modules/ui";

interface ProfilePageProps {
  context: BootstrapContext;
  user: UserAccount | null;
  onProfileUpdated: (updated: UserAccount) => void;
  onLogout: () => void;
}

const AVATAR_COLOR_OPTIONS: Array<{ id: AvatarColor; label: string }> = [
  { id: "teal", label: "翠绿 (默认)" },
  { id: "indigo", label: "深海靛蓝" },
  { id: "amber", label: "琥珀暖金" },
  { id: "rose", label: "勃艮第红" },
  { id: "violet", label: "极光紫罗兰" }
];

const TIMEZONE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "Asia/Shanghai", label: "Asia/Shanghai (UTC+08:00 北京/新加坡)" },
  { value: "UTC", label: "UTC (UTC+00:00 国际标准时间)" },
  { value: "America/New_York", label: "America/New_York (UTC-05:00 美东归因时区)" },
  { value: "America/Los_Angeles", label: "America/Los_Angeles (UTC-08:00 美西归因时区)" },
  { value: "Europe/London", label: "Europe/London (UTC+00:00 欧洲伦敦时区)" }
];

export function getAvatarInitial(account?: UserAccount | null, fallbackName?: string): string {
  const source = (account?.displayName || account?.name || fallbackName || "A").trim();
  return source ? source.charAt(0).toUpperCase() : "A";
}

export function getAvatarColorClass(color?: string): string {
  switch (color) {
    case "indigo":
      return "avatar-indigo";
    case "amber":
      return "avatar-amber";
    case "rose":
      return "avatar-rose";
    case "violet":
      return "avatar-violet";
    default:
      return "avatar-teal";
  }
}

export const ProfilePage: React.FC<ProfilePageProps> = ({
  context,
  user,
  onProfileUpdated,
  onLogout
}) => {
  const activeLogin = accountName() || user?.name || "local-admin";
  const [profile, setProfile] = useState<UserAccount | null>(user);
  const [loading, setLoading] = useState<boolean>(!user);
  const [saving, setSaving] = useState<boolean>(false);
  const [savingPwd, setSavingPwd] = useState<boolean>(false);

  const [displayName, setDisplayName] = useState<string>(user?.displayName || "");
  const [department, setDepartment] = useState<string>(user?.department || "");
  const [jobTitle, setJobTitle] = useState<string>(user?.jobTitle || "");
  const [email, setEmail] = useState<string>(user?.email || "");
  const [phone, setPhone] = useState<string>(user?.phone || "");
  const [timezone, setTimezone] = useState<string>(user?.timezone || "Asia/Shanghai");
  const [avatarColor, setAvatarColor] = useState<string>(user?.avatarColor || "teal");
  const [desc, setDesc] = useState<string>(user?.desc || "");

  const [currentPassword, setCurrentPassword] = useState<string>("");
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmPassword, setConfirmPassword] = useState<string>("");

  const syncFormState = useCallback((data: UserAccount) => {
    setProfile(data);
    setDisplayName(data.displayName || data.name || "");
    setDepartment(data.department || "海外商业化增长中心");
    setJobTitle(data.jobTitle || "商业化投放优化师");
    setEmail(data.email || `${data.name}@afs-media.com`);
    setPhone(data.phone || "+86 186-2109-8860");
    setTimezone(data.timezone || "Asia/Shanghai");
    setAvatarColor(data.avatarColor || "teal");
    setDesc(data.desc || "");
  }, []);

  const fetchProfile = useCallback(async () => {
    if (context.mode === "simulation") {
      const simProfile: UserAccount = {
        name: activeLogin,
        root: activeLogin === "reviewer" ? 0 : 1,
        partner: "AFS Simulation Workspace",
        status: 1,
        displayName: activeLogin === "reviewer" ? "陈静 · 预算审批官" : "林泽楷 · 模拟操作员",
        department: "海外商业化增长中心",
        jobTitle: activeLogin === "reviewer" ? "高级风控与预算审批官" : "全渠道买量负责人",
        email: `${activeLogin}@afs-media.com`,
        phone: "+86 186-2109-8860",
        timezone: "Asia/Shanghai",
        avatarColor: "teal",
        desc: "负责海外买量日常监控与智能体调优审批。",
        autoArticle: true,
        autoCampaign: true,
        showMaterial: true,
        siteList: ["all-fash", "all-quiz", "all-well", "core-fash"],
        activeSiteCount: 4,
        managedAccountCount: 8,
        managedCampaignCount: 24,
        recentOptimizationCount: 12,
        lastLoginAt: new Date().toISOString().slice(0, 19).replace("T", " ")
      };
      syncFormState(simProfile);
      onProfileUpdated(simProfile);
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const data = (await request("/cms/user/profile")) as UserAccount;
      syncFormState(data);
      onProfileUpdated(data);
    } catch (err) {
      if (err instanceof Error) toast(err.message);
    } finally {
      setLoading(false);
    }
  }, [activeLogin, context.mode, onProfileUpdated, syncFormState]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!displayName.trim()) {
      toast("请填写显示姓名");
      return;
    }
    setSaving(true);
    try {
      if (context.mode === "simulation") {
        const updated: UserAccount = {
          ...(profile || { name: activeLogin }),
          displayName: displayName.trim(),
          department: department.trim(),
          jobTitle: jobTitle.trim(),
          email: email.trim(),
          phone: phone.trim(),
          timezone,
          avatarColor,
          desc: desc.trim()
        };
        syncFormState(updated);
        onProfileUpdated(updated);
        toast("个人资料已更新");
      } else {
        const updated = (await request("/cms/user/profile", {
          method: "POST",
          body: {
            displayName: displayName.trim(),
            department: department.trim(),
            jobTitle: jobTitle.trim(),
            email: email.trim(),
            phone: phone.trim(),
            timezone,
            avatarColor,
            desc: desc.trim()
          }
        })) as UserAccount;
        syncFormState(updated);
        onProfileUpdated(updated);
        toast("个人资料已保存并实时同步至导航栏");
      }
    } catch (err) {
      toast(err instanceof Error ? err.message : "保存个人资料失败");
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    if (profile) {
      syncFormState(profile);
      toast("已恢复为上次保存的资料");
    }
  };

  const handleChangePassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (context.mode === "simulation") {
      toast("模拟工作区不支持修改密码");
      return;
    }
    if (!currentPassword.trim() || !newPassword.trim()) {
      toast("请填写当前密码和新密码");
      return;
    }
    if (newPassword.trim().length < 6) {
      toast("新密码长度不能少于 6 位");
      return;
    }
    if (newPassword.trim() !== confirmPassword.trim()) {
      toast("两次输入的新密码不一致");
      return;
    }
    setSavingPwd(true);
    try {
      await request("/cms/user/password", {
        method: "POST",
        body: {
          currentPassword: currentPassword.trim(),
          newPassword: newPassword.trim()
        }
      });
      setCredentials(activeLogin, newPassword.trim(), true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      toast("登录密码修改成功，当前会话已自动更新");
    } catch (err) {
      toast(err instanceof Error ? err.message : "修改密码失败");
    } finally {
      setSavingPwd(false);
    }
  };

  const isRoot = profile?.root === 1 || activeLogin === "local-admin";
  const sites = profile?.siteList || [];
  const adAccounts = profile?.boundAdAccounts || [];

  return (
    <div className="profile-page">
      <header className="page-head">
        <div>
          <p className="eyebrow">账号与个人中心</p>
          <h1>个人资料与权限中心</h1>
          <p className="sub">
            维护您的个人身份信息、工作时区、联系方式，并查看当前账号绑定的海外内容站点与买量广告账户范围。
          </p>
        </div>
        <div className="action-set">
          <button type="button" className="button secondary" onClick={fetchProfile} disabled={loading}>
            {loading ? "正在刷新…" : "刷新资料"}
          </button>
          <button type="button" className="button negative" onClick={onLogout}>
            退出当前登录
          </button>
        </div>
      </header>

      <section className="panel profile-hero" aria-label="账号身份卡片">
        <div className="profile-hero-main">
          <div className={`avatar-badge avatar-xl ${getAvatarColorClass(avatarColor)}`} aria-hidden="true">
            {getAvatarInitial({ ...(profile || { name: activeLogin }), displayName }, activeLogin)}
          </div>
          <div className="profile-hero-info">
            <div className="profile-title-row">
              <h2>{displayName || profile?.displayName || activeLogin}</h2>
              <span className="mono profile-handle">@{profile?.name === "local-admin" ? "admin" : (profile?.name || activeLogin)}</span>
              <span className={isRoot ? "badge teal" : "badge"}>
                {isRoot ? "系统超级管理员 (Root)" : "商业化投放优化师"}
              </span>
              <span className="badge teal">状态：正常启用</span>
            </div>
            <p className="profile-subline">
              <span>{department || "海外商业化增长中心"}</span>
              <span aria-hidden="true">·</span>
              <span>{jobTitle || "高级投放优化师"}</span>
              <span aria-hidden="true">·</span>
              <span>合作方：{profile?.partner || "AFS Media HQ"}</span>
            </p>
            <p className="profile-desc-preview">
              {desc || "负责海外全渠道（TikTok / Meta / Google / Taboola + AdSense）投放监控与商业化变现效率优化。"}
            </p>
          </div>
        </div>
        <div className="profile-hero-meta">
          <div>
            <small>工作邮箱</small>
            <strong>{email || `${activeLogin}@afs-media.com`}</strong>
          </div>
          <div>
            <small>偏好归因时区</small>
            <strong>{timezone}</strong>
          </div>
          <div>
            <small>最近核验登录</small>
            <strong className="mono">{profile?.lastLoginAt || "刚刚"}</strong>
          </div>
        </div>
      </section>

      <section className="cards" aria-label="个人运营资产概览">
        <article className="metric">
          <div className="metric-label">纳管在投广告活动</div>
          <div className="metric-value">{profile?.managedCampaignCount ?? 0}</div>
          <div className="metric-extra">
            {isRoot ? "具备全盘活动读写与规则绑定权限" : "当前工号名下负责的在投活动"}
          </div>
        </article>
        <article className="metric">
          <div className="metric-label">授权内容变现站点</div>
          <div className="metric-value">{profile?.activeSiteCount ?? sites.length}</div>
          <div className="metric-extra">可执行买量投放与 AdSense 变现对账的站点</div>
        </article>
        <article className="metric">
          <div className="metric-label">绑定平台买量账户</div>
          <div className="metric-value">{profile?.managedAccountCount ?? adAccounts.length}</div>
          <div className="metric-extra">涵盖 TikTok / Meta / Google / Taboola 渠道</div>
        </article>
        <article className="metric">
          <div className="metric-label">参与智能诊断与审批</div>
          <div className="metric-value">{profile?.recentOptimizationCount ?? 0}</div>
          <div className="metric-extra">累计发起或复核的智能体调优执行记录</div>
        </article>
      </section>

      <div className="profile-grid">
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>基本资料设置</h2>
              <p className="sub">修改后的姓名、职位与头像配色将实时呈现在顶部导航栏与审计日志中。</p>
            </div>
          </div>

          <form className="profile-form" onSubmit={handleSave}>
            <div className="form-grid">
              <label>
                <span>
                  显示姓名 / 昵称 <strong className="field-required">*</strong>
                </span>
                <input
                  name="displayName"
                  required
                  maxLength={120}
                  value={displayName}
                  onChange={e => setDisplayName(e.target.value)}
                  placeholder="例如：林泽楷 · 投放总监"
                />
              </label>

              <label>
                <span>登录账号 (唯一工号)</span>
                <input name="name" value={profile?.name || activeLogin} readOnly />
              </label>

              <label>
                <span>所属部门</span>
                <input
                  name="department"
                  maxLength={120}
                  value={department}
                  onChange={e => setDepartment(e.target.value)}
                  placeholder="例如：海外商业化增长中心"
                />
              </label>

              <label>
                <span>职位头衔</span>
                <input
                  name="jobTitle"
                  maxLength={120}
                  value={jobTitle}
                  onChange={e => setJobTitle(e.target.value)}
                  placeholder="例如：系统超级管理员 · 投放总控"
                />
              </label>

              <label>
                <span>工作邮箱</span>
                <input
                  type="email"
                  name="email"
                  maxLength={200}
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="name@afs-media.com"
                />
              </label>

              <label>
                <span>联系电话 / 企业微信</span>
                <input
                  name="phone"
                  maxLength={48}
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  placeholder="+86 186-2109-8860"
                />
              </label>

              <label className="all">
                <span>默认报表与归因时区</span>
                <select value={timezone} onChange={e => setTimezone(e.target.value)}>
                  {TIMEZONE_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>

              <div className="all avatar-picker-field">
                <span className="field-label">个性化头像配色</span>
                <div className="avatar-color-options" role="radiogroup" aria-label="个性化头像配色">
                  {AVATAR_COLOR_OPTIONS.map(option => (
                    <button
                      key={option.id}
                      type="button"
                      className={
                        avatarColor === option.id
                          ? "avatar-color-chip active"
                          : "avatar-color-chip"
                      }
                      onClick={() => setAvatarColor(option.id)}
                      aria-pressed={avatarColor === option.id}
                    >
                      <span
                        className={`avatar-dot ${getAvatarColorClass(option.id)}`}
                        aria-hidden="true"
                      />
                      <span>{option.label}</span>
                    </button>
                  ))}
                </div>
              </div>

              <label className="all">
                <span>个人职责与运营备注</span>
                <textarea
                  rows={3}
                  maxLength={240}
                  value={desc}
                  onChange={e => setDesc(e.target.value)}
                  placeholder="填写您负责的买量渠道、目标国家或审批职责说明…"
                />
              </label>
            </div>

            <div className="form-actions">
              <button type="submit" className="button" disabled={saving}>
                {saving ? "正在保存…" : "保存个人资料"}
              </button>
              <button type="button" className="button secondary" onClick={handleReset} disabled={saving}>
                还原修改
              </button>
            </div>
          </form>
        </section>

        <div className="profile-side-stack">
          <section className="panel">
            <div className="panel-head">
              <div>
                <h2>角色权限与安全鉴权</h2>
                <p className="sub">由系统管理员分配的操作权限矩阵与会话安全状态。</p>
              </div>
            </div>

            <div className="perm-badge-grid">
              <div className="perm-item">
                <span>账号角色级别</span>
                <span className={isRoot ? "badge teal" : "badge"}>
                  {isRoot ? "超级管理员 (全权限)" : "投放优化师 (站点隔离)"}
                </span>
              </div>
              <div className="perm-item">
                <span>文章内容自动过审</span>
                <span className={profile?.autoArticle ? "badge teal" : "badge gray"}>
                  {profile?.autoArticle ? "已授权" : "需人工复核"}
                </span>
              </div>
              <div className="perm-item">
                <span>广告活动自动发布</span>
                <span className={profile?.autoCampaign ? "badge teal" : "badge gray"}>
                  {profile?.autoCampaign ? "已授权" : "需人工复核"}
                </span>
              </div>
              <div className="perm-item">
                <span>素材中心与创意生成</span>
                <span className={profile?.showMaterial ? "badge teal" : "badge gray"}>
                  {profile?.showMaterial ? "已开启" : "受限访问"}
                </span>
              </div>
              <div className="perm-item">
                <span>渠道密钥库 &amp; 模型网关</span>
                <span className={isRoot ? "badge teal" : "badge amber"}>
                  {isRoot ? "可读写配置" : "只读权限"}
                </span>
              </div>
            </div>

            <dl className="security-meta-list">
              <div>
                <dt>登录安全保护</dt>
                <dd>加密令牌与防重放校验</dd>
              </div>
              <div>
                <dt>所属合作方 (Partner)</dt>
                <dd>{profile?.partner || "AFS Media HQ"}</dd>
              </div>
              <div>
                <dt>账号创建时间</dt>
                <dd className="mono">{profile?.ctime || "2026-01-01 09:00:00"}</dd>
              </div>
              <div>
                <dt>资料最近更新</dt>
                <dd className="mono">{profile?.utime || profile?.lastLoginAt || "刚刚"}</dd>
              </div>
            </dl>
          </section>

          {context.mode !== "simulation" && (
            <section className="panel">
              <div className="panel-head">
                <div>
                  <h2>修改登录密码</h2>
                  <p className="sub">设置您常用的个人登录密码，修改后立即生效且无需重新登录。</p>
                </div>
              </div>
              <form className="profile-form" onSubmit={handleChangePassword}>
                <div className="form-grid">
                  <label className="all">
                    <span>当前密码</span>
                    <input
                      type="password"
                      autoComplete="current-password"
                      required
                      value={currentPassword}
                      onChange={e => setCurrentPassword(e.target.value)}
                      placeholder="请输入当前登录密码"
                    />
                  </label>
                  <label>
                    <span>新密码（至少 6 位）</span>
                    <input
                      type="password"
                      autoComplete="new-password"
                      required
                      minLength={6}
                      maxLength={120}
                      value={newPassword}
                      onChange={e => setNewPassword(e.target.value)}
                      placeholder="请输入新密码"
                    />
                  </label>
                  <label>
                    <span>确认新密码</span>
                    <input
                      type="password"
                      autoComplete="new-password"
                      required
                      minLength={6}
                      maxLength={120}
                      value={confirmPassword}
                      onChange={e => setConfirmPassword(e.target.value)}
                      placeholder="再次输入新密码"
                    />
                  </label>
                </div>
                <div className="form-actions">
                  <button type="submit" className="button" disabled={savingPwd}>
                    {savingPwd ? "正在修改…" : "确认修改密码"}
                  </button>
                </div>
              </form>
            </section>
          )}

          <section className="panel">
            <div className="panel-head">
              <div>
                <h2>授权运营站点 ({sites.length})</h2>
                <p className="sub">当前账号可查看报表、创建文章与管理买量活动的站点列表。</p>
              </div>
              <a className="button secondary small" href="#settings">
                站点配置
              </a>
            </div>
            {sites.length > 0 ? (
              <div className="site-tag-list">
                {sites.map(site => (
                  <span key={site} className="site-tag">
                    <i className="status-dot" aria-hidden="true" />
                    <strong>{site}</strong>
                  </span>
                ))}
              </div>
            ) : (
              <div className="placeholder">暂无关联站点</div>
            )}
          </section>
        </div>
      </div>

      <section className="panel">
        <div className="panel-head">
          <div>
            <h2>绑定的平台买量广告账户 ({adAccounts.length})</h2>
            <p className="sub">当前账号可直接管理的 TikTok、Meta、Google 与 Taboola 广告主账户。</p>
          </div>
          <a className="button secondary small" href="#accounts">
            管理全部账户
          </a>
        </div>
        {adAccounts.length > 0 ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>广告平台</th>
                  <th>账户名称</th>
                  <th>平台 Account ID</th>
                  <th>所属合作方</th>
                  <th>主责优化师</th>
                </tr>
              </thead>
              <tbody>
                {adAccounts.slice(0, 12).map(acc => (
                  <tr key={`${acc.platform}-${acc.id}-${acc.accountId}`}>
                    <td>
                      <span className="badge teal">{acc.platform}</span>
                    </td>
                    <td>
                      <strong>{acc.name}</strong>
                    </td>
                    <td className="mono">{acc.accountId}</td>
                    <td>{acc.partner || "AFS Media"}</td>
                    <td>{acc.operator || activeLogin}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="placeholder">当前账号暂未单独绑定买量广告账户。</div>
        )}
      </section>
    </div>
  );
};
