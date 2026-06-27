import React, { useState } from "react";
import { BootstrapContext, UserAccount } from "../types/cms";
import { getLastAccountName, request, setCredentials } from "../modules/api";

interface LoginViewProps {
  context: BootstrapContext;
  onAuthenticated: (user: UserAccount | null) => Promise<void> | void;
}

const CMS_QUICK_ACCOUNTS: Array<{ name: string; role: string }> = [
  { name: "admin", role: "超级管理员 · 投放总控" },
  { name: "alex.chen", role: "TikTok 买量负责人" },
  { name: "sarah.lin", role: "Google 投放专家" },
  { name: "david.wu", role: "Meta 增长负责人" }
];

const SIM_QUICK_ACCOUNTS: Array<{ name: string; role: string }> = [
  { name: "operator", role: "投放操作员" },
  { name: "reviewer", role: "预算审批员" }
];

export const LoginView: React.FC<LoginViewProps> = ({ context, onAuthenticated }) => {
  const simulated = context.mode === "simulation";
  const defaultAccount = getLastAccountName(
    simulated ? "operator" : "admin"
  );

  const [name, setName] = useState<string>(defaultAccount);
  const [secret, setSecret] = useState<string>("");
  const [showSecret, setShowSecret] = useState<boolean>(false);
  const [persistSession, setPersistSession] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage("");
    const trimmed = name.trim();
    if (simulated ? !["operator", "reviewer"].includes(trimmed) : !/^[\x21-\x7e]+$/.test(trimmed)) {
      setErrorMessage("请填写有效的管理员或优化师账号");
      return;
    }
    if (!secret.trim()) {
      setErrorMessage("请输入密码");
      return;
    }
    setSubmitting(true);
    setCredentials(trimmed, secret.trim(), persistSession);
    try {
      const user = await request(simulated ? "/cms/agent/runs" : "/cms/user/login");
      setSecret("");
      await onAuthenticated(simulated ? null : (user as UserAccount));
    } catch (err) {
      setCredentials("", "", false);
      setErrorMessage(err instanceof Error ? err.message : "登录失败，请检查账号或密码");
    } finally {
      setSubmitting(false);
    }
  };

  const insecure = !simulated && !window.isSecureContext;
  const quickAccounts = simulated ? SIM_QUICK_ACCOUNTS : CMS_QUICK_ACCOUNTS;

  return (
    <section className="login-screen" id="login-screen" aria-labelledby="login-title">
      <div className="login-split">
        <aside className="login-showcase" aria-label="平台能力介绍">
          <div className="login-showcase-head">
            <div className="login-brand-row">
              <span className="brand-mark" aria-hidden="true">
                <i />
                <i />
                <i />
              </span>
              <div>
                <strong>AFS 商业化投放平台</strong>
                <small>Overseas AdOps &amp; Monetization Console</small>
              </div>
            </div>
            <span className="login-env-badge">
              {simulated ? "演示工作区" : "企业生产环境"}
            </span>
          </div>

          <div className="login-showcase-hero">
            <h2>海外多渠道买量与变现一体化运营中台</h2>
            <p>
              统一打通 TikTok、Meta、Google、Taboola 四大广告平台与 AdSense 变现收入数据，实现从实时漏斗归因、自动化预算护栏到智能诊断决策的闭环管理。
            </p>
          </div>

          <div className="login-feature-list">
            <article className="login-feature-card">
              <span className="login-feature-icon" aria-hidden="true">▦</span>
              <div>
                <h3>跨平台全漏斗 ROI 归因</h3>
                <p>实时聚合买量消耗、点击转化与变现结算收入，精准核算单站点、单国家与单活动真实利润。</p>
              </div>
            </article>
            <article className="login-feature-card">
              <span className="login-feature-icon" aria-hidden="true">≋</span>
              <div>
                <h3>自动化预算护栏与止损熔断</h3>
                <p>支持多条件分时调价、异常高耗自动预警与放量阈值控制，守护买量资金安全。</p>
              </div>
            </article>
            <article className="login-feature-card">
              <span className="login-feature-icon" aria-hidden="true">✦</span>
              <div>
                <h3>智能副驾驶与归因诊断</h3>
                <p>秒级巡检全盘低效活动与未绑规则风险，自动生成带证据链与人工审批护栏的调优方案。</p>
              </div>
            </article>
            <article className="login-feature-card">
              <span className="login-feature-icon" aria-hidden="true">◈</span>
              <div>
                <h3>企业级权限管控与审计追踪</h3>
                <p>全链路加密鉴权与防重放保护，按站点和账户精细隔离权限，完整留存团队操作与审批记录。</p>
              </div>
            </article>
          </div>

          <div className="login-showcase-foot">
            <span className="login-status-pill">
              <i className="status-dot" aria-hidden="true" />
              核心网关与数据库在线
            </span>
            <span>支持站点级权限隔离与多角色协同</span>
          </div>
        </aside>

        <div className="login-panel login-card">
          <div className="login-card-head">
            <p className="eyebrow">账号登录</p>
            <h1 id="login-title">欢迎登录控制台</h1>
            <p className="sub">
              {simulated
                ? "请选择操作员或审批员身份并输入密码进入工作台。"
                : "请输入您的管理员或投放优化师账号及密码登录。"}
            </p>
          </div>

          {insecure ? (
            <div className="error">
              当前连接未加密，请通过 HTTPS 安全协议访问管理台。
            </div>
          ) : (
            <>
              <div className="quick-account-box">
                <span className="quick-account-label">快捷填充账号：</span>
                <div className="quick-account-chips">
                  {quickAccounts.map(item => (
                    <button
                      key={item.name}
                      type="button"
                      className={name.trim() === item.name ? "quick-chip active" : "quick-chip"}
                      onClick={() => {
                        setName(item.name);
                        setErrorMessage("");
                      }}
                      title={item.role}
                    >
                      <strong>{item.name}</strong>
                      <small>{item.role}</small>
                    </button>
                  ))}
                </div>
              </div>

              <form className="login-form" onSubmit={handleSubmit}>
                <label>
                  <span>管理员 / 优化师账号</span>
                  <input
                    name="name"
                    autoComplete="username"
                    required
                    maxLength={120}
                    autoFocus
                    value={name}
                    onChange={e => setName(e.target.value)}
                    placeholder={
                      simulated ? "operator 或 reviewer" : "例如：admin 或 alex.chen"
                    }
                  />
                </label>

                <label>
                  <span>密码</span>
                  <div className="secret-input">
                    <input
                      type={showSecret ? "text" : "password"}
                      name="secret"
                      autoComplete="current-password"
                      required
                      maxLength={240}
                      value={secret}
                      onChange={e => setSecret(e.target.value)}
                      placeholder="请输入密码"
                    />
                    <button
                      type="button"
                      className="secret-toggle"
                      aria-label="显示或隐藏密码"
                      onClick={() => setShowSecret(prev => !prev)}
                    >
                      {showSecret ? "隐藏" : "显示"}
                    </button>
                  </div>
                </label>

                <label className="login-remember">
                  <input
                    type="checkbox"
                    checked={persistSession}
                    onChange={e => setPersistSession(e.target.checked)}
                  />
                  <span>保持当前浏览器会话登录（刷新页面无需重复输入密码）</span>
                </label>

                <div id="login-error" role="alert">
                  {errorMessage && <div className="alert error">{errorMessage}</div>}
                </div>

                <button className="button login-submit-btn" type="submit" disabled={submitting}>
                  {submitting ? "正在登录…" : "登录控制台"}
                </button>
              </form>

              <div className="login-card-foot">
                <span>登录后可在「个人资料」中心查看权限矩阵、绑定账户并维护个人信息。</span>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
};
