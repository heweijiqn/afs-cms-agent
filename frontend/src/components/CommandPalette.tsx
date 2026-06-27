import React, { useEffect, useMemo, useRef, useState } from "react";
import { NAV_SECTIONS } from "../types/cms";
import { accountName, requestList } from "../modules/api";
import { openAgentTab } from "../modules/agent";

interface CommandItem {
  id: string;
  group: string;
  title: string;
  subtitle: string;
  badge?: string;
  action: () => void;
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

const AGENT_SHORTCUTS: Array<{ tab: string; title: string; subtitle: string }> = [
  { tab: "scan", title: "智能体 · 全盘异常巡检", subtitle: "扫描急需止损、放量机会与未绑规则活动" },
  { tab: "five-agent", title: "智能体 · 五智能体闭环优化", subtitle: "Monitor / Audience / Creative / Bidding / 凸优化分配" },
  { tab: "workflow", title: "智能体 · 分析与审批看板", subtitle: "8 信号决策卡、审批指纹、幂等执行与一键回滚" },
  { tab: "copilot", title: "智能体 · 智能副驾驶对话", subtitle: "自然语言跨平台排查与一键下发工单" },
  { tab: "replay", title: "智能体 · 离线回放评估 (Uplift)", subtitle: "真实历史数据反事实回放与 4-Gate 上线门禁" },
  { tab: "observability", title: "智能体 · 运行统计与追踪", subtitle: "查看各节点耗时、调用次数与执行明细" }
];

export const CommandPalette: React.FC<CommandPaletteProps> = ({ open, onClose }) => {
  const [query, setQuery] = useState<string>("");
  const [selectedIndex, setSelectedIndex] = useState<number>(0);
  const [campaigns, setCampaigns] = useState<any[]>([]);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    setSelectedIndex(0);
    setTimeout(() => inputRef.current?.focus(), 20);
  }, [open]);

  useEffect(() => {
    if (!open || !accountName()) return;
    const trimmed = query.trim();
    const timer = setTimeout(() => {
      const qs = trimmed ? `?query=${encodeURIComponent(trimmed)}&limit=6` : "?limit=5";
      requestList(`/cms/agent/campaigns${qs}`)
        .then(rows => setCampaigns(Array.isArray(rows) ? rows : []))
        .catch(() => setCampaigns([]));
    }, 160);
    return () => clearTimeout(timer);
  }, [open, query]);

  const items = useMemo<CommandItem[]>(() => {
    const q = query.trim().toLowerCase();
    const list: CommandItem[] = [];

    for (const section of NAV_SECTIONS) {
      for (const nav of section.items) {
        if (!q || nav.label.toLowerCase().includes(q) || nav.id.toLowerCase().includes(q) || section.group.toLowerCase().includes(q)) {
          list.push({
            id: `nav-${nav.id}`,
            group: "页面导航",
            title: `${nav.icon} ${nav.label}`,
            subtitle: section.group,
            badge: `#${nav.id}`,
            action: () => {
              window.location.hash = `#${nav.id}`;
              onClose();
            }
          });
        }
      }
    }

    for (const sc of AGENT_SHORTCUTS) {
      if (!q || sc.title.toLowerCase().includes(q) || sc.subtitle.toLowerCase().includes(q) || sc.tab.includes(q)) {
        list.push({
          id: `agent-${sc.tab}`,
          group: "智能体快捷入口",
          title: sc.title,
          subtitle: sc.subtitle,
          badge: "AI",
          action: () => {
            openAgentTab(sc.tab);
            onClose();
          }
        });
      }
    }

    for (const c of campaigns) {
      const cid = String(c.campaignId || "");
      const name = String(c.name || cid);
      const platform = String(c.platform || "");
      const roas = c.roas != null ? `${Number(c.roas).toFixed(2)}×` : "—";
      list.push({
        id: `camp-${cid}`,
        group: "在投广告活动（点击进入五智能体诊断）",
        title: `${cid} · ${name}`,
        subtitle: `${platform} · 站点 ${c.site || "—"} · ROAS ${roas}`,
        badge: platform || "活动",
        action: () => {
          openAgentTab("five-agent");
          onClose();
        }
      });
    }

    return list.slice(0, 14);
  }, [query, campaigns, onClose]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [items.length, query]);

  if (!open) return null;

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelectedIndex(prev => (items.length ? (prev + 1) % items.length : 0));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelectedIndex(prev => (items.length ? (prev - 1 + items.length) % items.length : 0));
    } else if (event.key === "Enter" && items[selectedIndex]) {
      event.preventDefault();
      items[selectedIndex].action();
    }
  };

  return (
    <div
      className="cmd-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="全局快捷命令面板"
      onClick={e => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={handleKeyDown}
    >
      <div className="cmd-modal">
        <div className="cmd-search-row">
          <span className="cmd-search-icon" aria-hidden="true">⌕</span>
          <input
            ref={inputRef}
            type="search"
            className="cmd-input"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="搜索页面、智能体功能或输入活动 ID / 名称快速跳转…"
          />
          <kbd className="cmd-kbd">ESC</kbd>
        </div>
        <div className="cmd-list" role="listbox">
          {items.length === 0 ? (
            <div className="cmd-empty">未找到匹配项，试试输入「巡检」「报表」「活动 ID」…</div>
          ) : (
            items.map((item, idx) => {
              const showGroup = idx === 0 || items[idx - 1].group !== item.group;
              return (
                <React.Fragment key={item.id}>
                  {showGroup && <div className="cmd-group-label">{item.group}</div>}
                  <button
                    type="button"
                    role="option"
                    aria-selected={idx === selectedIndex}
                    className={idx === selectedIndex ? "cmd-item active" : "cmd-item"}
                    onMouseEnter={() => setSelectedIndex(idx)}
                    onClick={item.action}
                  >
                    <span className="cmd-item-text">
                      <strong>{item.title}</strong>
                      <small>{item.subtitle}</small>
                    </span>
                    {item.badge && <span className="badge gray mono">{item.badge}</span>}
                  </button>
                </React.Fragment>
              );
            })
          )}
        </div>
        <div className="cmd-footer">
          <span>↑ ↓ 选择 · Enter 打开 · Esc 关闭</span>
          <span>Ctrl + K 随时唤起</span>
        </div>
      </div>
    </div>
  );
};
