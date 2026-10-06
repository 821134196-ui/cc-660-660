<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { STATUS, SEVERITY, IMPACT_STATUS, fmtTime } from '../labels.js';

  let code = '';
  let result = null;
  let error = '';
  let notice = '';
  let workerName = '';
  let impactDesc = '';
  let impactSeverity = 'UNKNOWN';
  let reporter = '';
  let showImpactForm = false;
  let allCodes = [];

  onMount(async () => {
    try {
      allCodes = (await api.helmets()).map((h) => h.code);
    } catch { /* 后端未就绪时忽略 */ }
  });

  async function doScan(target) {
    error = '';
    notice = '';
    result = null;
    showImpactForm = false;
    const c = (target ?? code).trim();
    if (!c) { error = '请输入或扫描编号'; return; }
    code = c;
    try {
      result = await api.scan(c);
      workerName = '';
    } catch (e) {
      error = e.message;
    }
  }

  // 本地模拟扫码枪：随机取一个已登记编号填入并查询
  function simulateScan() {
    if (!allCodes.length) { error = '暂无已登记编号'; return; }
    const c = allCodes[Math.floor(Math.random() * allCodes.length)];
    doScan(c);
  }

  async function issue() {
    error = ''; notice = '';
    if (!workerName.trim()) { error = '请填写领用人姓名'; return; }
    try {
      await api.issue(result.id, workerName.trim());
      notice = `已发放给 ${workerName.trim()}`;
      await doScan();
    } catch (e) { error = e.message; }
  }

  async function returnBack() {
    error = ''; notice = '';
    try {
      await api.returnHelmet(result.id);
      notice = '已归还入库';
      await doScan();
    } catch (e) { error = e.message; }
  }

  async function reportImpact() {
    error = ''; notice = '';
    try {
      await api.reportImpact(result.id, {
        description: impactDesc,
        severity: impactSeverity,
        reportedBy: reporter
      });
      notice = '撞击已上报，安全帽已立即冻结，等待安全员检查';
      showImpactForm = false;
      impactDesc = ''; reporter = ''; impactSeverity = 'UNKNOWN';
      await doScan();
    } catch (e) { error = e.message; }
  }

  $: st = result ? STATUS[result.status] : null;
</script>

<div class="card">
  <h2>仓库扫码收发</h2>
  <div class="row">
    <input
      style="flex:1;min-width:240px;font-size:18px;padding:12px"
      placeholder="扫描或输入安全帽编号，如 HMT-1001"
      bind:value={code}
      on:keydown={(e) => e.key === 'Enter' && doScan()} />
    <button style="padding:12px 28px" on:click={() => doScan()}>查询</button>
    <button class="secondary" style="padding:12px 20px" on:click={simulateScan}>📷 模拟扫码</button>
  </div>
  {#if error}<div class="error-msg">{error}</div>{/if}
  {#if notice}<div class="ok-msg">{notice}</div>{/if}
</div>

{#if result && st}
  <div class="card" style="border-left:8px solid {st.color}">
    <div class="row" style="justify-content:space-between">
      <div>
        <div style="font-size:26px;font-weight:800">{result.code}</div>
        <div class="muted">批次 {result.batch} ｜ 生产 {result.manufactureDate} ｜ 使用期限至 {result.serviceLifeEndDate}
          {#if result.serviceLifeExpired}<span class="badge" style="background:#fee2e2;color:#b91c1c">已超期</span>{/if}
        </div>
      </div>
      <div class="badge" style="background:{st.bg};color:{st.color};font-size:20px;padding:10px 22px">
        {st.label}
      </div>
    </div>

    {#if !result.canIssue}
      <div class="error-msg" style="font-size:16px;font-weight:600">⛔ {result.blockReason}</div>
    {/if}

    {#if result.currentHolder}
      <p>当前领用人：<b>{result.currentHolder}</b></p>
    {/if}

    {#if result.openEvent}
      <div class="card" style="background:#fffbeb;margin:12px 0">
        <h3>⚠️ 未结案撞击事件 #{result.openEvent.id}（{IMPACT_STATUS[result.openEvent.status]}）</h3>
        <table>
          <tr><th style="width:120px">上报时间</th><td>{fmtTime(result.openEvent.reportedAt)}</td></tr>
          <tr><th>撞击程度</th><td>{SEVERITY[result.openEvent.severity]}</td></tr>
          <tr><th>当时使用人</th><td>{result.openEvent.userAtImpact ?? '库存中（无人领用）'}</td></tr>
          <tr><th>情况描述</th><td>{result.openEvent.description ?? '—'}</td></tr>
        </table>
        <p class="muted">请前往 <a href="#/inspect/{result.openEvent.id}">检查工作台</a> 处理该事件。</p>
      </div>
    {/if}

    {#if result.status === 'RETIRED'}
      <div class="card" style="background:#fef2f2;margin:12px 0">
        <h3>退役信息</h3>
        <table>
          <tr><th style="width:120px">退役时间</th><td>{fmtTime(result.retiredAt)}</td></tr>
          <tr><th>退役依据</th><td>{result.retireReason ?? '—'}</td></tr>
        </table>
        <p class="muted">退役结论不可撤销，该编号永久保留，不能重新登记或发放。</p>
      </div>
    {/if}

    {#if result.nextInspectionDate}
      <p>下次检查日期：<b>{result.nextInspectionDate}</b></p>
    {/if}

    <div class="row" style="margin-top:16px">
      {#if result.canIssue}
        <input placeholder="领用人姓名" bind:value={workerName} />
        <button on:click={issue}>确认发放</button>
      {/if}
      {#if result.status === 'ISSUED'}
        <button class="secondary" on:click={returnBack}>归还入库</button>
      {/if}
      {#if result.status === 'ISSUED' || result.status === 'IN_STOCK'}
        <button class="danger" on:click={() => (showImpactForm = !showImpactForm)}>上报撞击</button>
      {/if}
      <a href="#/helmets/{result.id}" class="muted" style="margin-left:auto">查看完整档案 →</a>
    </div>

    {#if showImpactForm}
      <div class="card" style="background:#fff7ed;margin-top:12px">
        <h3>上报撞击（上报后立即冻结）</h3>
        <div class="row" style="margin-bottom:10px">
          <label>撞击程度：
            <select bind:value={impactSeverity}>
              <option value="UNKNOWN">程度不明</option>
              <option value="MINOR">轻微</option>
              <option value="MODERATE">中等</option>
              <option value="SEVERE">严重</option>
            </select>
          </label>
          <input placeholder="上报人" bind:value={reporter} />
        </div>
        <textarea style="width:100%" rows="2" placeholder="撞击情况描述" bind:value={impactDesc}></textarea>
        <div style="margin-top:10px">
          <button class="danger" on:click={reportImpact}>确认上报并冻结</button>
        </div>
        <p class="muted">程度不明时安全员不能直接判定复用，需复检或退役。</p>
      </div>
    {/if}
  </div>
{/if}
