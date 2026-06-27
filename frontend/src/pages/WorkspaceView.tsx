import React, { useEffect, useRef } from "react";
import { BootstrapContext, ROUTE_TITLES, RouteView, UserAccount } from "../types/cms";
import { accountName } from "../modules/api";
import { closeDialog, toast } from "../modules/ui";
import { overview, reports, collection, releaseChart } from "../modules/cms";
import { agent, refreshRuns } from "../modules/agent";
import { catalog } from "../modules/catalog";
import { workbench } from "../modules/business";
import { archiveReport } from "../modules/archive";
import { channelVault } from "../modules/local-tools";
import { platformReference } from "../modules/platform-reference";
import { ProfilePage } from "./ProfilePage";

interface WorkspaceViewProps {
  route: RouteView;
  context: BootstrapContext;
  onRequestLogin: () => void;
  onRetryBootstrap: () => void;
  onProfileUpdated: (updated: UserAccount) => void;
  onLogout: () => void;
}

export const WorkspaceView: React.FC<WorkspaceViewProps> = ({
  route,
  context,
  onRequestLogin,
  onRetryBootstrap,
  onProfileUpdated,
  onLogout
}) => {
  const mainRef = useRef<HTMLElement | null>(null);
  const revisionRef = useRef<number>(0);

  useEffect(() => {
    const modalEl = document.querySelector<HTMLDialogElement>("#modal");
    if (modalEl?.open) closeDialog();

    releaseChart();
    document.title = `${ROUTE_TITLES[route] || "运营总览"} · AFS 投放管理`;

    if (route === "profile" || context.cmsUnavailable) {
      return;
    }

    const main = mainRef.current;
    if (!main) return;

    const current = ++revisionRef.current;
    const isCurrent = () => current === revisionRef.current;

    const mountView = async () => {
      if (route === "overview") {
        overview(main, context, [], isCurrent);
        if (
          context.mode === "simulation" &&
          !context.hasArchiveData &&
          (!context.requireStaffLogin || accountName())
        ) {
          try {
            const runs = await refreshRuns();
            if (isCurrent()) overview(main, context, runs);
          } catch (err) {
            if (isCurrent() && err instanceof Error) toast(err.message);
          }
        }
      } else if (route === "reports") {
        if (context.mode === "simulation") {
          await archiveReport(main, context, isCurrent, onRequestLogin, true);
        } else {
          reports(main, context);
        }
      } else if (route === "platform-reference") {
        await platformReference(main, context, isCurrent);
      } else if (route === "vault") {
        await channelVault(main, context, isCurrent);
      } else if (route === "content" || route === "accounts" || route === "reportconfig") {
        catalog(main, context, route, isCurrent);
      } else if (route === "dictionaries" || route === "feedback" || route === "system") {
        workbench(main, context, route);
      } else if (route === "dimensions") {
        workbench(main, context, "reports");
      } else if (route === "agent") {
        await agent(main, context, isCurrent, onRequestLogin);
      } else {
        await collection(main, context, route, isCurrent);
      }
    };

    mountView().catch(err => {
      if (isCurrent() && err instanceof Error) toast(err.message);
    });

    main.focus({ preventScroll: true });

    return () => {
      releaseChart();
    };
  }, [route, context, onRequestLogin]);

  if (context.cmsUnavailable) {
    return (
      <main id="main" key="unavailable-workspace" tabIndex={-1}>
        <header className="page-head">
          <div>
            <p className="eyebrow">AFS 投放管理</p>
            <h1>无法连接 CMS</h1>
            <p className="sub">{context.cmsUnavailable}</p>
          </div>
        </header>
        <button type="button" className="button" id="retry" onClick={onRetryBootstrap}>
          重试连接
        </button>
      </main>
    );
  }

  if (route === "profile") {
    return (
      <main id="main" key="react-profile-workspace" tabIndex={-1}>
        <ProfilePage
          context={context}
          user={context.account || null}
          onProfileUpdated={onProfileUpdated}
          onLogout={onLogout}
        />
      </main>
    );
  }

  return (
    <main id="main" key="imperative-workspace" ref={mainRef} tabIndex={-1}>
      <div className="placeholder">正在加载视图…</div>
    </main>
  );
};
