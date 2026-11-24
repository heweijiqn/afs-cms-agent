import {request} from './api.js';
import {$,escape as e,dialog,closeDialog,formValues,busy,errorIn,toast} from './ui.js';

export function openCohortImport(refresh:()=>void) {
  const panel=dialog('导入归因队列',`<p class="sub">每行是一条累计观测。campaignId、platform、cohortDate、ageDay、acquisitions、spendUsd、cumulativeRevenueUsd 必填，activeUsers 可留空。花费和获客数在同一队列各行须相同，累计收入不能下降；金额单位为 USD。</p>
    <button type="button" id="cohort-template" class="button secondary">下载 CSV 表头</button>
    <form><label>归因数据文件<input name="file" type="file" accept=".csv,.json" required></label><div class="import-error"></div><button class="button" type="submit">导入并校验</button></form>`);
  $('#cohort-template',panel).onclick=()=>{
    const url=URL.createObjectURL(new Blob(['\uFEFFcampaignId,platform,cohortDate,ageDay,acquisitions,spendUsd,cumulativeRevenueUsd,activeUsers\r\n'],{type:'text/csv;charset=utf-8'}));
    const a=document.createElement('a');a.href=url;a.download='cohort-observations.csv';a.click();URL.revokeObjectURL(url);
  };
  const form=$('form',panel) as HTMLFormElement;
  form.onsubmit=ev=>{ev.preventDefault();busy($('button[type=submit]',form),async()=>{
    const file=(form.elements.namedItem('file') as HTMLInputElement).files?.[0];if(!file)throw new Error('请选择文件');
    const text=(await file.text()).replace(/^\uFEFF/,'');
    const rows=file.name.endsWith('.json')?JSON.parse(text):csvRecords(text);
    const result=await request('/cms/agent/overseas/cohort/import',{body:rows});toast(`已导入 ${result.saved} 条归因观测`);closeDialog();refresh();
  },msg=>errorIn($('.import-error',form),msg));};
}

function csvRecords(text:string,required?:string[]) {
  const rows:string[][]=[];let row:string[]=[],cell='',quoted=false;
  for(let i=0;i<text.length;i++) {const c=text[i];
    if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;}
    else if(c===','&&!quoted){row.push(cell.trim());cell='';}
    else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell.trim());if(row.some(Boolean))rows.push(row);row=[];cell='';}
    else cell+=c;
  }
  if(quoted)throw new Error('CSV 引号未闭合');row.push(cell.trim());if(row.some(Boolean))rows.push(row);
  const headers=rows.shift()||[];
  const expected=required||['campaignId','platform','cohortDate','ageDay','acquisitions','spendUsd','cumulativeRevenueUsd'];
  if(expected.some(k=>!headers.includes(k))||new Set(headers).size!==headers.length)throw new Error('CSV 缺少必填列或列名重复，请下载表头');
  return rows.map((r,index)=>{if(r.length!==headers.length)throw new Error(`CSV 第 ${index+2} 行列数不符`);return Object.fromEntries(headers.map((h,i)=>[h,r[i]]));});
}

export function openEvidenceImport(kind:'accounts'|'landing'|'experiments',refresh:()=>void) {
  const fields={accounts:['accountId','platform','reportDate','currency','balance','dailySpend','accountStatus','rejectedAds'],landing:['campaignId','reportDate','url','ttfbMs','sessions','bounces','conversions'],experiments:['draftId','variant','reportDate','impressions','clicks','conversions','spendUsd','revenueUsd']}[kind];
  const title={accounts:'账户余额与状态',landing:'落地页观测',experiments:'多组实验观测'}[kind];
  const panel=dialog(`导入${title}`,`<p class="sub">按日导入 CSV 或 JSON，重复的同日观测会替换。${kind==='experiments'?'variant=A 为对照组，B/C 等为实验组；同一 draftId 下可以有多个实验组。此入口不分发广告流量。':kind==='accounts'?'accountId 必须是账号库的现有账号；dailySpend 和 balance 使用 currency 币种。accountStatus 填 ACTIVE/ENABLED 或平台返回状态。':'campaignId 必须是活动库中的现有编号；TTFB 单位为毫秒。此入口读取测量结果。'}</p><p class="sub">列：${e(fields.join(', '))}</p>
    <button type="button" class="button secondary" id="evidence-template">下载 CSV 表头</button><form><label>文件<input type="file" name="file" accept=".csv,.json" required></label><div class="import-error"></div><button type="submit" class="button">导入观测</button></form>`);
  $('#evidence-template',panel).onclick=()=>{const url=URL.createObjectURL(new Blob(['\uFEFF'+fields.join(',')+'\r\n'],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`${kind}-observations.csv`;a.click();URL.revokeObjectURL(url);};
  const form=$('form',panel) as HTMLFormElement;
  form.onsubmit=ev=>{ev.preventDefault();busy($('button[type=submit]',form),async()=>{const file=(form.elements.namedItem('file') as HTMLInputElement).files?.[0];if(!file)throw new Error('请选择文件');const text=(await file.text()).replace(/^\uFEFF/,'');const rows=file.name.endsWith('.json')?JSON.parse(text):csvRecords(text,fields);await request(`/cms/agent/overseas/observations/${kind}`,{body:rows});closeDialog();refresh();},msg=>errorIn($('.import-error',form),msg));};
}

export function openFxRates(refresh:()=>void) {
  const panel=dialog('录入有日期的汇率',`<p class="sub">填写 1 USD 对应的目标币种数量及来源。只换算展示，不修改原始花费；未录入的币种不会使用默认汇率。</p><form class="form-grid">
    <label>币种<select name="currency"><option>CNY</option><option>EUR</option><option>GBP</option><option>JPY</option></select></label>
    <label>汇率<input name="rate" type="number" min="0.00000001" step="any" required></label>
    <label>日期<input name="date" type="date" required></label><label>来源<input name="source" required maxlength="200"></label>
    <div class="fx-error all"></div><button type="submit" class="button">保存汇率</button></form>`);
  const form=$('form',panel) as HTMLFormElement;
  form.onsubmit=ev=>{ev.preventDefault();busy($('button',form),async()=>{const v=formValues(form);await request('/cms/agent/overseas/currencies',{body:{date:v.date,source:v.source,rates:{[v.currency]:Number(v.rate)}}});closeDialog();refresh();},msg=>errorIn($('.fx-error',form),msg));};
}

const types=[['STOP_LOSS','空耗止损'],['SCALE_UP','达标提预算'],['FATIGUE_ROTATE','CTR 衰退提醒'],['OFF_PEAK_SAVER','低峰预算折扣']];
export function openAutomationRule(rule:any,refresh:()=>void) {
  const c=rule?JSON.parse(rule.conditionJson||'{}'):{};const p=rule?JSON.parse(rule.actionParamsJson||'{}'):{};
  const input=(name:string,title:string,value:any,step='any')=>`<label>${title}<input type="number" name="${name}" value="${e(value)}" min="0" step="${step}" required></label>`;
  const panel=dialog(rule?'编辑规则':'新建规则',`<p class="sub">按所选报告时区评估当日观测，每条规则对同一活动每天触发一次。直接执行只更新 TikTok 广告组；其他平台及素材轮换生成待人工处理记录。</p>
    <form class="form-grid"><label>名称<input name="name" value="${e(rule?.name||'')}" required maxlength="160"></label>
    <label>规则<select name="ruleType">${types.map(([v,t])=>`<option value="${v}" ${rule?.ruleType===v?'selected':''}>${t}</option>`).join('')}</select></label>
    <label>平台<select name="scopePlatform">${['ALL','Google','Facebook','TikTok','Taboola'].map(v=>`<option ${rule?.scopePlatform===v?'selected':''}>${v}</option>`).join('')}</select></label>
    <label>报告时区<select name="timezone">${['UTC','Asia/Shanghai','America/Los_Angeles','America/New_York','Europe/London','Asia/Tokyo'].map(v=>`<option ${c.timezone===v?'selected':''}>${v}</option>`).join('')}</select></label>
    <div class="rule-conditions all form-grid"></div>
    <label>运行模式<select name="dryRun"><option value="true">仅试算</option><option value="false" ${rule?.dryRun===false?'selected':''}>直接执行</option></select></label>
    <label>状态<select name="enabled"><option value="true">启用</option><option value="false" ${rule?.enabled===false?'selected':''}>禁用</option></select></label>
    <div class="rule-error all"></div><button type="submit" class="button">保存规则</button></form>`);
  const form=$('form',panel) as HTMLFormElement;const type=form.elements.namedItem('ruleType') as HTMLSelectElement;
  function render() {
    const box=$('.rule-conditions',form);
    box.innerHTML=type.value==='STOP_LOSS'?input('minSpend','当日花费 ≥ USD',c.minSpend??50)+input('maxConversions','当日转化 ≤',c.maxConversions??0,'1')
      :type.value==='SCALE_UP'?input('minRoas','ROAS ≥',c.minRoas??1.3)+input('minPacingPct','预算使用率 ≥ %',c.minPacingPct??85)+input('budgetDeltaPct','提高预算 %',p.budgetDeltaPct??15)+input('maxDailyBudget','广告组预算上限 USD',p.maxDailyBudget??600)
      :type.value==='FATIGUE_ROTATE'?input('minImpressions','当日曝光 ≥',c.minImpressions??1000,'1')+input('ctrDeclinePct','相对前 7 日 CTR 下降 ≥ %',c.ctrDeclinePct??25)
      :`<label>低峰开始<input name="start" type="time" value="${e((c.offPeakHours||'01:00-06:00').split('-')[0])}" required></label><label>低峰结束<input name="end" type="time" value="${e((c.offPeakHours||'01:00-06:00').split('-')[1])}" required></label>`+input('bidDiscountPct','降低预算 %',p.bidDiscountPct??30);
  }type.onchange=render;render();
  form.onsubmit=ev=>{ev.preventDefault();busy($('button[type=submit]',form),async()=>{
    const v=formValues(form);const conditions:any={timezone:v.timezone},params:any={};
    for(const k of ['minSpend','maxConversions','minRoas','minPacingPct','minImpressions','ctrDeclinePct'])if(v[k]!==undefined)conditions[k]=Number(v[k]);
    for(const k of ['budgetDeltaPct','maxDailyBudget','bidDiscountPct'])if(v[k]!==undefined)params[k]=Number(v[k]);
    if(v.start)conditions.offPeakHours=`${v.start}-${v.end}`;
    const action=v.ruleType==='STOP_LOSS'?'PAUSE_CAMPAIGN':v.ruleType==='FATIGUE_ROTATE'?'SWAP_CREATIVE':'ADJUST_BUDGET';
    await request('/cms/agent/overseas/automation/rules',{body:{id:rule?.id,name:v.name,ruleType:v.ruleType,scopePlatform:v.scopePlatform,conditionJson:JSON.stringify(conditions),actionType:action,actionParamsJson:JSON.stringify(params),dryRun:v.dryRun==='true',enabled:v.enabled==='true'}});
    closeDialog();refresh();
  },msg=>errorIn($('.rule-error',form),msg));};
}
