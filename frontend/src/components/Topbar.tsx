import React from "react";
import { NAV_SECTIONS, ROUTE_TITLES, RouteView, UserAccount } from "../types/cms";
import { getAvatarColorClass, getAvatarInitial } from "../pages/ProfilePage";

function resolveBreadcrumbGroup(route: RouteView): string {
  const mapped: RouteView =
    route === "reportconfig"
      ? "settings"
      : route === "platform-reference" || route === "dimensions"
      ? "reports"
      : route;
  for (const section of NAV_SECTIONS) {
    if (section.items.some(item => item.id === mapped)) {
      return section.group;
    }
  }
  return "投放运营";
}

interface TopbarProps {
  activeRoute: RouteView;
  showAccountButton: boolean;
  accountName: string;
  userAccount?: UserAccount | null;
  onLogoutClick: () => void;
  onOpenCommand: () => void;
}

export const Topbar: React.FC<TopbarProps> = ({
  activeRoute,
  showAccountButton,
  accountName,
  userAccount,
  onLogoutClick,
  onOpenCommand
}) => {
  const displayLabel = userAccount?.displayName || accountName || "未登录";
  const roleLabel =
    userAccount?.jobTitle ||
    (userAccount?.root === 1 || accountName === "local-admin" ? "超级管理员" : "个人资料");
  const avatarClass = getAvatarColorClass(userAccount?.avatarColor);
  const initial = getAvatarInitial(userAccount, accountName);

  return (
    <header className="topbar">
      <div className="breadcrumb">
        <span>{resolveBreadcrumbGroup(activeRoute)}</span>
        <span aria-hidden="true">/</span>
        <strong id="breadcrumb">{ROUTE_TITLES[activeRoute] || "运营总览"}</strong>
      </div>
      <div className="top-actions">
        <button
          type="button"
          className="cmd-trigger-btn"
          onClick={onOpenCommand}
          title="打开全局快捷搜索与命令面板 (Ctrl + K)"
        >
          <span aria-hidden="true">⌕</span>
          <span>搜索页面 / 活动 / 智能体…</span>
          <kbd className="cmd-kbd">Ctrl K</kbd>
        </button>
        {showAccountButton && (
          <div className="topbar-user-group">
            <a
              href="#profile"
              className={
                activeRoute === "profile"
                  ? "topbar-profile-btn active"
                  : "topbar-profile-btn"
              }
              id="account"
              title="查看与编辑个人资料"
            >
              <span className={`avatar-badge avatar-sm ${avatarClass}`} aria-hidden="true">
                {initial}
              </span>
              <span className="topbar-user-meta">
                <strong>{displayLabel}</strong>
                <small>{roleLabel}</small>
              </span>
            </a>
            {accountName && (
              <button
                className="button secondary small topbar-logout-btn"
                type="button"
                onClick={onLogoutClick}
                title="安全退出当前账号"
              >
                退出
              </button>
            )}
          </div>
        )}
      </div>
    </header>
  );
};
