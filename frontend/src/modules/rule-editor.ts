import { escape as e } from "./ui.js";

const metrics = { AD_SPEND: "广告花费", ROI: "ROI", REVENUE: "收入", PROFIT: "利润" };
const comparisons = { ">": "大于", ">=": "大于或等于", "<": "小于", "<=": "小于或等于", "==": "等于", "!=": "不等于" };
const actions = { CLOSE_CAMPAIGN: "暂停广告组", CHANGE_BUDGET: "调整预算" };
const directions = { INCREASE: "提高预算", DECREASE: "降低预算" };
const joins = { "&&": "并且", "||": "或者" };
const blankCondition: { join?: string; metric: string; comparison: string; threshold: string } = { metric: "", comparison: "", threshold: "" };
const conditionPattern = /^(AD_SPEND|ROI|REVENUE|PROFIT)\s*(>=|<=|==|!=|>|<)\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)$/;

function select(label, key, choices, value) {
  return `<label>${label}<select data-rule-field="${key}" required><option value="">请选择</option>${
    Object.entries(choices).map(([id, title]) =>
      `<option value="${e(id)}"${value === id ? " selected" : ""}>${e(title)}</option>`).join("")
  }</select></label>`;
}

function number(label, key, value, bounds = 'step="any"') {
  return `<label>${label}<input type="number" data-rule-field="${key}" value="${e(value)}" required ${bounds}></label>`;
}

function button(label, command) {
  return `<button type="button" class="button secondary small" data-rule-command="${command}">${label}</button>`;
}

function parseCondition(condition) {
  if (typeof condition !== "string") return null;
  const parts = condition.split(/(&&|\|\|)/);
  const clauses = [];
  for (let index = 0; index < parts.length; index += 2) {
    const match = conditionPattern.exec(parts[index].trim());
    if (!match || !Number.isFinite(Number(match[3]))) return null;
    const threshold = match[3].replace(/^\+/, "").replace(/\.(?=[eE]|$)/, ".0");
    clauses.push({ join: parts[index - 1], metric: match[1], comparison: match[2], threshold });
  }
  return clauses;
}

export function ruleSummary(rule) {
  if (!Array.isArray(rule) || !rule.length) return '<p class="placeholder">暂无规则条件</p>';
  return rule.map(window => `<section class="rule-summary"><h3>投放第 ${e(window.dayStart)} 至 ${e(window.dayEnd)} 天</h3><ol>${
    (window.detailList || []).map(detail => {
      const clauses = parseCondition(detail.condition);
      const condition = clauses?.map(item =>
        [joins[item.join], metrics[item.metric], comparisons[item.comparison], item.threshold].filter(Boolean).join(" ")).join(" ") || detail.condition;
      const action = detail.action === "CHANGE_BUDGET"
        ? `${directions[detail.budgetAction] || actions[detail.action]} ${detail.budgetPercent ?? "—"}%`
        : actions[detail.action] || detail.action;
      return `<li>${e(condition)} → ${e(action)}</li>`;
    }).join("")
  }</ol></section>`).join("");
}

export function mountRuleEditor(container, initialRule) {
  const original = new WeakMap();

  function addClause(detail: any, clause: any = blankCondition) {
    const list = detail.querySelector(".rule-clauses");
    list.insertAdjacentHTML("beforeend", `<div class="rule-clause form-grid">
      ${list.children.length ? select("连接方式", "join", joins, clause.join || "&&") : ""}
      ${select("指标", "metric", metrics, clause.metric)}
      ${select("比较符", "comparison", comparisons, clause.comparison)}
      ${number("阈值", "threshold", clause.threshold)}
      ${list.children.length ? button("移除条件", "remove-clause") : ""}
    </div>`);
  }

  function syncBudget(detail) {
    const active = value(detail, "action") === "CHANGE_BUDGET";
    const budget = detail.querySelector(".rule-budget");
    budget.hidden = !active;
    budget.querySelectorAll("input, select").forEach(control => { control.disabled = !active; });
  }

  function addDetail(window: any, source?: any) {
    const list = window.querySelector(".rule-actions");
    const detail = document.createElement("div");
    detail.className = "rule-action";
    list.append(detail);
    if (source) original.set(detail, source);
    const clauses = source ? parseCondition(source.condition) : [blankCondition];
    if (source && (!clauses || !Object.hasOwn(actions, source.action) ||
        (source.action === "CHANGE_BUDGET" && !Object.hasOwn(directions, source.budgetAction)))) {
      detail.classList.add("rule-legacy");
      detail.innerHTML = `<p class="sub">旧版条件将按原配置保存。需要替换时可移除此项，再添加新的条件与动作。</p>
        <p>${e(source.condition)} · ${e(actions[source.action] || source.action)}</p>`;
    } else {
      detail.innerHTML = `<div class="rule-clauses"></div>${button("添加条件", "add-clause")}
        <div class="form-grid rule-action-fields">
          ${select("执行动作", "action", actions, source?.action)}
          <div class="rule-budget all"><div class="form-grid">
            ${select("预算方向", "budgetAction", directions, source?.budgetAction)}
            ${number("预算调整幅度（%）", "budgetPercent", source?.budgetPercent, 'min="1" max="100" step="1"')}
          </div></div>
        </div>`;
      clauses.forEach(clause => addClause(detail, clause));
      syncBudget(detail);
    }
    detail.insertAdjacentHTML("beforeend", button("移除此项", "remove-action"));
  }

  function addWindow(source?: any) {
    const window = document.createElement("fieldset");
    window.className = "operation-batch rule-window";
    if (source) original.set(window, source);
    window.innerHTML = `<legend>投放天数区间</legend><div class="form-grid">
      ${number("开始天数", "dayStart", source?.dayStart ?? 1, 'min="1" step="1"')}
      ${number("结束天数", "dayEnd", source?.dayEnd ?? 1, 'min="1" step="1"')}
    </div><div class="rule-actions"></div>
    <div class="form-actions">${button("添加条件与动作", "add-action")}${button("移除区间", "remove-window")}</div>`;
    container.querySelector(".rule-windows").append(window);
    if (source?.detailList?.length) source.detailList.forEach(detail => addDetail(window, detail));
    else addDetail(window);
  }

  function reset(rule?: any) {
    container.innerHTML = `<h3>条件与动作</h3><div class="rule-windows"></div>${button("添加天数区间", "add-window")}`;
    if (Array.isArray(rule) && rule.length) rule.forEach(addWindow);
    else addWindow();
  }

  container.onclick = event => {
    const command = event.target.dataset.ruleCommand;
    const window = event.target.closest(".rule-window");
    const detail = event.target.closest(".rule-action");
    switch (command) {
      case "add-window": addWindow(); break;
      case "add-action": addDetail(window); break;
      case "add-clause": addClause(detail); break;
      case "remove-window": window.remove(); break;
      case "remove-action": detail.remove(); break;
      case "remove-clause": event.target.closest(".rule-clause").remove(); break;
    }
  };
  container.onchange = event => {
    if (event.target.dataset.ruleField === "action") syncBudget(event.target.closest(".rule-action"));
  };

  function readDetail(detail) {
    const source = original.get(detail);
    if (detail.classList.contains("rule-legacy")) return source;
    const condition = [...detail.querySelectorAll(".rule-clause")].map(clause => {
      const threshold = value(clause, "threshold");
      if (!threshold || !Number.isFinite(Number(threshold))) throw new Error("请填写有效的数字阈值");
      return [value(clause, "join"), value(clause, "metric"), value(clause, "comparison"), threshold].filter(Boolean).join(" ");
    }).join(" ");
    const action = value(detail, "action");
    const result = { ...source, action, condition };
    if (action === "CHANGE_BUDGET") {
      result.budgetAction = value(detail, "budgetAction");
      result.budgetPercent = Number(value(detail, "budgetPercent"));
    } else {
      delete result.budgetAction;
      delete result.budgetPercent;
    }
    return result;
  }

  function read() {
    const windows = [...container.querySelectorAll(".rule-window")];
    if (!windows.length) throw new Error("请添加至少一个投放天数区间");
    return windows.map(window => {
      const dayStart = Number(value(window, "dayStart"));
      const dayEnd = Number(value(window, "dayEnd"));
      if (dayStart > dayEnd) throw new Error("结束天数不能小于开始天数");
      const detailList = [...window.querySelectorAll(".rule-action")].map(readDetail);
      if (!detailList.length) throw new Error("每个区间需要至少一项条件与动作");
      return { ...original.get(window), dayStart, dayEnd, detailList };
    });
  }

  reset(initialRule);
  return { read, reset };
}

function value(container, key) {
  return container.querySelector(`[data-rule-field="${key}"]`)?.value;
}
