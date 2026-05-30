import React, { useCallback, useEffect, useState } from "react";
import { BootstrapContext, UserAccount } from "./types/cms";
import { useHashRoute } from "./hooks/useHashRoute";
import { Sidebar } from "./components/Sidebar";
import { Topbar } from "./components/Topbar";
import { LoginView } from "./components/LoginView";
import { ModalHost } from "./components/ModalHost";
import { CommandPalette } from "./components/CommandPalette";
import { WorkspaceView } from "./pages/WorkspaceView";
import {
  accountName,
  request,
  restoreSessionCredentials,
  setCredentials,
  setMode
} from "./modules/api";
import { closeDialog, toast } from "./modules/ui";
import { clearCmsData } from "./modules/cms";
import { resetAgent } from "./modules/agent";

export const App: React.FC = () => {
  const [route] = useHashRoute();
  const [context, setContext] = useState<BootstrapContext | null>(null);
  const [authenticated, setAuthenticated] = useState<boolean>(false);
  const [currentUser, setCurrentUser] = useState<string>("");
  const [cmdOpen, setCmdOpen] = useState<boolean>(false);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCmdOpen(prev => !prev);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const requiresLogin = useCallback((ctx: BootstrapContext | null) => {
    return Boolean(ctx && (ctx.mode === "cms" || ctx.requireStaffLogin));
  }, []);

  const loadArchive = useCallback(
    async (ctx: BootstrapContext, isAuth: boolean) => {
      if (!ctx.hasArchiveData || ((ctx.mode === "cms" || ctx.requireStaffLogin) && !isAuth)) {
        return null;
      }
      try {
        return (await request("/cms/console/archive-data")) as Record<string, unknown[]>;
      } catch {
        return null;
      }
    },
    []
  );

  const initialize = useCallback(async () => {
    try {
      const boot = (await request("/cms/console/bootstrap", { anonymous: true })) as BootstrapContext;
      if (!boot.mode || !["simulation", "cms"].includes(boot.mode)) {
        throw new Error("无法识别 CMS 运行模式");
      }
      setMode(boot.mode);

      let restoredAccount: UserAccount | null = boot.account || null;
      let isAuth = false;

      if (restoreSessionCredentials() && accountName()) {
        try {
          const verified = await request(
            boot.mode === "simulation" ? "/cms/agent/runs" : "/cms/user/login"
          );
          isAuth = true;
          if (boot.mode !== "simulation" && verified) {
            restoredAccount = verified as UserAccount;
          }
        } catch {
          setCredentials("", "", false);
          isAuth = false;
        }
      }

      const archiveData = await loadArchive(boot, isAuth);
      setAuthenticated(isAuth);
      setCurrentUser(isAuth ? accountName() : "");
      setContext({ ...boot, account: restoredAccount, archiveData });
    } catch (err) {
      setContext({
        cmsUnavailable: err instanceof Error ? err.message : "连接 CMS 失败"
      });
    }
  }, [loadArchive]);

  useEffect(() => {
    initialize();
  }, [initialize]);

  const handleAuthenticated = useCallback(
    async (user: UserAccount | null) => {
      setAuthenticated(true);
      setCurrentUser(accountName());
      if (context) {
        const archiveData = await loadArchive(context, true);
        setContext(prev => (prev ? { ...prev, account: user, archiveData } : prev));
      }
    },
    [context, loadArchive]
  );

  const handleProfileUpdated = useCallback((updated: UserAccount) => {
    setContext(prev => (prev ? { ...prev, account: updated } : prev));
  }, []);

  const handleLogout = useCallback(() => {
    setAuthenticated(false);
    setCredentials("", "", false);
    setCurrentUser("");
    clearCmsData();
    resetAgent();
    closeDialog();
    setContext(prev => (prev ? { ...prev, account: null, archiveData: null } : prev));
    toast("已安全退出登录");
  }, []);

  const needsLogin = Boolean(
    context && !context.cmsUnavailable && requiresLogin(context) && !authenticated
  );

  if (needsLogin && context) {
    document.title = "登录 · AFS 商业化投放管理平台";
  }

  return (
    <>
      {!needsLogin && (
        <a className="skip" href="#main" id="workspace-skip">
          跳转至主要内容
        </a>
      )}

      {needsLogin && context ? (
        <LoginView context={context} onAuthenticated={handleAuthenticated} />
      ) : (
        <div className="shell" id="workspace">
          <Sidebar
            activeRoute={route}
            accountName={currentUser}
            userAccount={context?.account}
            context={context}
          />
          <div className="workspace">
            <Topbar
              activeRoute={route}
              showAccountButton={requiresLogin(context)}
              accountName={currentUser}
              userAccount={context?.account}
              onLogoutClick={handleLogout}
              onOpenCommand={() => setCmdOpen(true)}
            />
            {context ? (
              <WorkspaceView
                route={route}
                context={context}
                onRequestLogin={handleLogout}
                onRetryBootstrap={initialize}
                onProfileUpdated={handleProfileUpdated}
                onLogout={handleLogout}
              />
            ) : (
              <main id="main" tabIndex={-1}>
                <div className="placeholder">正在连接 AFS CMS…</div>
              </main>
            )}
          </div>
        </div>
      )}

      <CommandPalette open={!needsLogin && cmdOpen} onClose={() => setCmdOpen(false)} />
      <ModalHost />
    </>
  );
};
