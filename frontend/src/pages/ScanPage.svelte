<script>
  import { api, fmtDate, fmtDateTime } from '../api.js';
  import { showToast } from '../toast.js';

  // 本地模拟扫码：输入编号回车 / 点演示码芯片 / “模拟扫码枪”随机扫一个库内编号
  let code = '';
  let detail = null;
  let loading = false;
  let quickList = [];
  let workerName = '';
  let returnNote = '';
  let busy = false;

  const demoCodes = ['AQM-1001', 'AQM-1002', 'AQM-1003', 'AQM-1004', 'AQM-1005', 'AQM-1006'];

  async function loadQuickList() {
    try {
      quickList = await api.listHelmets();
    } catch (e) { /* 首页静默 */ }
  }
  loadQuickList();

  async function scan(target) {
    const c = (target ?? code).trim();
    if (!c) {
      showToast('请输入或扫描安全帽编号');
      return;
    }
    code = c.toUpperCase();
    loading = true;
    detail = null;
    try {
      detail = await api.detail(code);
    } catch (e) {
      if (e.status === 404) {
        showToast(`编号 ${code} 不存在：请先在“库存档案”登记，退役旧码无法重新登记`);
      } else {
        showToast(e.message);
      }
    } finally {
      loading = false;
    }
  }

  // 模拟扫码枪：从当前库存随机取一个编号填入并查询
  function simulateScanner() {
    if (!quickList.length) {
      showToast('库存为空，请先登记安全帽');
      return;
    }
    const pick = quickList[Math.floor(Math.random() * quickList.length)];
    code = pick.code;
    scan(code);
  }

  async function checkout() {
    if (!workerName.trim()) {
      showToast('领用人姓名不能为空');
      return;
    }
    busy = true;
    try {
      await api.checkout(code, { workerName: workerName.trim(), team: '' });
      showToast('领出登记成功', 'ok');
      workerName = '';
      await scan(code);
      await loadQuickList();
    } catch (e) {
      showToast(e.message);
    } finally {
      busy = false;
    }
  }

  async function doReturn() {
    busy = true;
    try {
      await api.return(code, { note: returnNote.trim() });
      showToast('已归还入库', 'ok');
      returnNote = '';
      await scan(code);
      await loadQuickList();
    } catch (e) {
      showToast(e.message);
    } finally {
      busy = false;
    }
  }

  $: helmet = detail?.helmet;
  $: latestImpact = detail?.impacts?.[0];
  $: openCheckout = detail?.checkouts?.find((c) => c.open);
</script>

<div class="panel">
  <h1>仓库扫码发放</h1>
  <p class="muted small">模拟扫码：扫码枪输入编号后回车，或点下方演示码。冻结、复检中、退役的安全帽无法领出。</p>
  <div class="scan-hero">
    <div class="scan-input">
      <input placeholder="扫描 / 输入安全帽编号，如 AQM-1003"
             bind:value={code}
             on:keydown={(e) => e.key === 'Enter' && scan()}
             class="mono" />
      <button class="primary" on:click={() => scan()} disabled={loading}>查询</button>
      <button on:click={simulateScanner} title="模拟扫码枪随机扫到一顶库内帽子">📷 模拟扫码</button>
    </div>
  </div>
  <div class="chips">
    {#each demoCodes as d}
      <span class="chip" on:click={() => scan(d)}>{d}</span>
    {/each}
  </div>
</div>

{#if loading}
  <div class="panel muted">查询中…</div>
{/if}

{#if helmet}
  <div class="panel">
    <div class="bigstatus {helmet.status}">
      <div class="code">{helmet.code}</div>
      <div class="stxt">
        {#if helmet.status === 'IN_SERVICE'}{helmet.canCheckout ? '✅ 在役 · 允许领出' : '⚠ 在役但被拦截'}
        {:else if helmet.status === 'FROZEN'}⛔ 受冲击已冻结 · 禁止领出
        {:else if helmet.status === 'REINSPECTING'}⛔ 复检验证中 · 仍冻结 · 禁止领出
        {:else if helmet.status === 'RETIRED'}🚫 已退役 · 旧编号永久作废
        {/if}
      </div>
      {#if helmet.blockReason}
        <div style="margin-top:8px;font-weight:600;">拦截原因：{helmet.blockReason}</div>
      {/if}
    </div>

    <dl class="kv">
      <dt>生产批次</dt><dd class="mono">{helmet.batch}</dd>
      <dt>生产日期</dt><dd>{fmtDate(helmet.manufactureDate)}
        {#if helmet.serviceLifeExpired}<span class="badge RETIRED" style="margin-left:8px;">已超使用年限</span>{/if}
      </dd>
      <dt>下次检查日</dt><dd>{fmtDate(helmet.nextInspectionDate)}</dd>
      <dt>当前持有人</dt>
      <dd>{openCheckout ? `${openCheckout.workerName}${openCheckout.team ? '（' + openCheckout.team + '）' : ''}，${fmtDateTime(openCheckout.checkedOutAt)} 领出` : '在库'}</dd>
      <dt>最近撞击</dt><dd>{latestImpact ? `${fmtDateTime(latestImpact.occurredAt)} · ${latestImpact.severityLabel} · 使用人：${latestImpact.holderName}` : '无'}</dd>
    </dl>

    <!-- 发放/归还操作区：服务端已做强制校验，前端只做显隐与禁用 -->
    <div style="margin-top:16px;">
      {#if openCheckout}
        <div class="callout info">该帽在 {openCheckout.workerName} 手中{helmet.status !== 'IN_SERVICE' ? '，已被冻结，收回后不得再发放，须走安全员检查' : ''}</div>
        <label>归还备注</label>
        <input bind:value={returnNote} placeholder="如：工人交回，帽顶有磕碰" />
        <div style="margin-top:10px;">
          <button class="primary" on:click={doReturn} disabled={busy}>登记归还入库</button>
        </div>
      {:else if helmet.canCheckout}
        <div class="callout go">状态正常，可以领出。领用人：</div>
        <div class="row">
          <input bind:value={workerName} placeholder="领用人姓名（必填）"
                 on:keydown={(e) => e.key === 'Enter' && checkout()} />
        </div>
        <div style="margin-top:10px;">
          <button class="primary" on:click={checkout} disabled={busy}>确认领出</button>
        </div>
      {:else}
        <div class="callout stop">
          {#if helmet.status === 'RETIRED'}该编号已退役作废，任何领用、重新登记请求都会被系统拦截。
          {:else if helmet.status === 'REINSPECTING'}复检退回期间保持冻结，须由安全员复检后判定复用或退役。
          {:else}该帽已冻结，须经安全员检查外壳、内衬与使用年限后方可处置。{/if}
        </div>
        <div class="small muted">操作指引：
          {#if helmet.status === 'RETIRED'}<span class="tag">不可操作</span>
          {:else}<a href="#/inspect?code={helmet.code}">前往安全员检查 →</a>{/if}
          &nbsp;|&nbsp; <a href="#/impact?code={helmet.code}">补充撞击上报</a>
        </div>
      {/if}
    </div>
  </div>

  {#if latestImpact && helmet.status !== 'RETIRED'}
    <div class="panel">
      <h3 class="section-title">最近一次撞击事故</h3>
      <dl class="kv">
        <dt>当时使用人</dt><dd><strong>{latestImpact.holderName}</strong></dd>
        <dt>撞击程度</dt><dd>{latestImpact.severityLabel}{latestImpact.severity === 'UNKNOWN' ? '（不明 → 按冻结处理，不默认合格）' : ''}</dd>
        <dt>地点</dt><dd>{latestImpact.location || '—'}</dd>
        <dt>描述</dt><dd>{latestImpact.description || '—'}</dd>
      </dl>
    </div>
  {/if}
{/if}
