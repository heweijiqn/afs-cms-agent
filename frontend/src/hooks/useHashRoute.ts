import { useEffect, useState } from "react";
import { ROUTE_TITLES, RouteView } from "../types/cms";

function parseHash(): RouteView {
  const raw = window.location.hash.slice(1);
  return Object.prototype.hasOwnProperty.call(ROUTE_TITLES, raw)
    ? (raw as RouteView)
    : "overview";
}

export function useHashRoute(): [RouteView, (next: RouteView) => void] {
  const [route, setRoute] = useState<RouteView>(() => parseHash());

  useEffect(() => {
    const onHashChange = () => {
      if (window.location.hash === "#main") return;
      setRoute(parseHash());
    };
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const navigate = (next: RouteView) => {
    window.location.hash = `#${next}`;
  };

  return [route, navigate];
}
