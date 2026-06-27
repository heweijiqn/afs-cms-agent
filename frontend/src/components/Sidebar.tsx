import React from "react";
import { BootstrapContext, NAV_SECTIONS, RouteView, UserAccount } from "../types/cms";
import { getAvatarColorClass, getAvatarInitial } from "../pages/ProfilePage";

interface SidebarProps {
  activeRoute: RouteView;
  accountName: string;
  userAccount?: UserAccount | null;
  context: BootstrapContext | null;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeRoute,
  accountName,
  userAccount,
  context
}) => {
  const highlightedNav: RouteView =
    activeRoute === "reportconfig"
      ? "settings"
      : activeRoute === "platform-reference" || activeRoute === "dimensions"
      ? "reports"
      : activeRoute;

  const displayLabel = userAccount?.displayName || accountName || "未登录";
  const subLabel =
    userAccount?.department ||
    (context?.mode === "simulation" ? "模拟工作区" : "AFS 投放管理平台");
  const avatarClass = getAvatarColorClass(userAccount?.avatarColor);
  const initial = getAvatarInitial(userAccount, accountName);

  return (
    <aside className="sidebar">
      <a className="brand" href="#overview" aria-label="AFS 投放管理首页">
        <span className="brand-mark" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span>
          <strong>AFS</strong>
          <small>投放管理</small>
        </span>
      </a>

      <nav className="navigation" aria-label="主导航">
        {NAV_SECTIONS.map(section => (
          <React.Fragment key={section.group}>
            <p>{section.group}</p>
            {section.items.map(item => (
              <a
                key={item.id}
                href={`#${item.id}`}
                data-nav={item.id}
                aria-current={highlightedNav === item.id ? "page" : undefined}
              >
                <span className="nav-icon" aria-hidden="true">
                  {item.icon}
                </span>
                {item.label}
              </a>
            ))}
          </React.Fragment>
        ))}
      </nav>

      <a className="sidebar-footer sidebar-profile-card" href="#profile" title="前往个人资料中心">
        <span className={`avatar-badge avatar-sm ${avatarClass}`} aria-hidden="true">
          {initial}
        </span>
        <div className="sidebar-user-text">
          <strong id="sidebar-account">{displayLabel}</strong>
          <small id="sidebar-mode">{subLabel}</small>
        </div>
      </a>
    </aside>
  );
};
