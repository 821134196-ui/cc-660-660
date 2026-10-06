<script>
  import { api, fmtDate, fmtDateTime } from '../api.js';
  import { showToast } from '../toast.js';

  let helmets = [];
  let filter = '';
  let loading = false;

  let showRegister = false;
  let reg = { code: '', batch: '', manufactureDate: '' };
  let busy = false;

  let selected = null;
  let editBatch = '';
  let editDate = '';
  let editSaving = false;

  async function load() {
    loading = true;
    try {
      helmets = await api.listHelmets(filter || undefined);
    } catch (e) {
      showToast(e.message);
    } finally {
      loading = false;
    }
  }
  load();

  async function register() {
    if (!reg.code.trim() || !reg.batch.trim() || !reg.manufactureDate) {
      showToast('编号、生产批次、生产日期均为必填');
      return;
    }
    busy = true;
    try {
      await api.register({
        code: reg.code.trim().toUpperCase(),
        batch: reg.batch.trim(),
        manufactureDate: reg.manufactureDate
      });
      showToast(`安全帽 ${reg.code.toUpperCase()} 登记成功`, 'ok');
      reg = { code: '', batch: '', manufactureDate: '' };
      showRegister = false;
      await load();
    } catch (e) {
      showToast(e.message); // 退役旧码会在此被拦截
    } finally {
      busy = false;
    }
  }

  async function open(h) {
    try {
      selected = await api.detail(h.code);
      editBatch = selected.helmet.batch;
      editDate = selected.helmet.manufactureDate;
    } catch (e) {
      showToast(e.message);
    }
  }

  async function saveInventory() {
    editSaving = true;
    try {
      await api.update(selected.helmet.code, {
        batch: editBatch.trim(),
        manufactureDate: editDate
      });
      showToast('库存资料已更新（状态不可通过此入口修改）', 'ok');
      await open({ code: selected.helmet.code });
      await load();
    } catch (e) {
      showToast(e.message);
    } finally {
      editSaving = false;
    }
  }

  const filters = [
    { value: '', label: '全部' },
    { value: 'IN_SERVICE', label: '在役' },
    { value: 'FROZEN', label: '冻结' },
    { value: 'REINSPECTING', label: '复检中' },
    { value: 'RETIRED', label: '已退役' }
  ];
</script>

<div class="panel">
  <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
    <h1 style="margin:0;">安全帽库存档案</h1>
    <div style="display:flex;gap:8px;align-items:center;">
      <div class="tabs" style="margin:0;">
        {#each filters as f}
          <button class="sm" class:active={filter === f.value} on:click={() => (filter = f.value, load())}>
            {f.label}
          </button>
        {/each}
      </div>
      <button class="primary" on:click={() => (showRegister = !showRegister)}>＋ 登记新帽</button>
    </div>
  </div>

  {#if showRegister}
    <div style="margin-top:16px;border-top:1px solid var(--line);padding-top:14px;">
      <h3 class="section-title">登记安全帽（唯一编号 + 生产批次）</h3>
      <div class="grid2">
        <input class="mono" placeholder="唯一编号，如 AQM-1007" bind:value={reg.code} />
        <input placeholder="生产批次，如 B2025-09" bind:value={reg.batch} />
      </div>
      <label>生产日期</label>
      <input type="date" bind:value={reg.manufactureDate} style="max-width:260px;" />
      <div style="margin-top:10px;">
        <button class="primary" on:click={register} disabled={busy}>确认登记</button>
        <span class="muted small" style="margin-left:10px;">已退役的旧编号无法重新登记，系统会直接拦截</span>
      </div>
    </div>
  {/if}
</div>

<div class="panel">
  <table>
    <thead>
      <tr><th>编号</th><th>批次</th><th>生产日期</th><th>状态</th><th>当前持有人</th><th>下次检查</th><th></th></tr>
    </thead>
    <tbody>
      {#if loading}
        <tr><td colspan="7" class="muted">加载中…</td></tr>
      {:else if helmets.length === 0}
        <tr><td colspan="7" class="muted">没有符合条件的安全帽</td></tr>
      {/if}
      {#each helmets as h}
        <tr>
          <td class="mono" style="font-weight:700;">{h.code}</td>
          <td>{h.batch}</td>
          <td>{fmtDate(h.manufactureDate)}{#if h.serviceLifeExpired}<br /><span class="badge RETIRED">超年限</span>{/if}</td>
          <td>
            <span class="badge {h.status}">{h.statusLabel}</span>
            {#if !(h.canCheckout && h.status === 'IN_SERVICE')}
              {#if h.status === 'IN_SERVICE' && h.blockReason}
                <div class="small" style="color:var(--red);">{h.blockReason}</div>
              {/if}
            {/if}
          </td>
          <td>{h.currentHolder || '—'}</td>
          <td>{fmtDate(h.nextInspectionDate)}</td>
          <td><button class="sm" on:click={() => open(h)}>档案</button></td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

{#if selected}
  <div class="panel">
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
      <h2 class="mono" style="margin:0;">{selected.helmet.code}</h2>
      <span class="badge {selected.helmet.status}">{selected.helmet.statusLabel}</span>
    </div>

    <div class="grid2" style="margin-top:16px;">
      <div>
        <h3 class="section-title">库存资料编辑（不含状态）</h3>
        <label>生产批次</label>
        <input bind:value={editBatch} disabled={editSaving} />
        <label>生产日期</label>
        <input type="date" bind:value={editDate} disabled={editSaving} />
        <div style="margin-top:10px;">
          <button class="primary sm" on:click={saveInventory} disabled={editSaving}>保存资料</button>
        </div>
        <p class="small muted" style="margin-top:8px;">
          此接口不接受 status 字段，夹带 status 的请求会被拒绝；退役结论不能通过编辑库存撤销。
        </p>
      </div>
      <div>
        <h3 class="section-title">关键信息</h3>
        <dl class="kv">
          <dt>使用年限</dt>
          <dd>{selected.helmet.serviceLifeExpired ? '已超 3 年年限' : '未超期（3 年）'}</dd>
          <dt>下次检查日</dt><dd>{fmtDate(selected.helmet.nextInspectionDate)}</dd>
          <dt>最近撞击</dt><dd>{fmtDateTime(selected.helmet.lastImpactAt)}</dd>
          <dt>退役时间</dt><dd>{fmtDateTime(selected.helmet.retiredAt)}</dd>
        </dl>
      </div>
    </div>
  </div>

  <div class="grid2">
    <div class="panel">
      <h3 class="section-title">领用历史（{selected.checkouts.length}）</h3>
      <table>
        <thead><tr><th>领用人</th><th>班组</th><th>领出</th><th>归还</th></tr></thead>
        <tbody>
          {#each selected.checkouts as c}
            <tr>
              <td>{c.workerName}{#if c.open}<span class="tag" style="background:var(--blue-bg);color:#1c4db3;">使用中</span>{/if}</td>
              <td class="small">{c.team || '—'}</td>
              <td class="small">{fmtDateTime(c.checkedOutAt)}</td>
              <td class="small">{c.returnedAt ? fmtDateTime(c.returnedAt) : '—'}</td>
            </tr>
          {:else}
            <tr><td colspan="4" class="muted small">暂无领用记录</td></tr>
          {/each}
        </tbody>
      </table>
    </div>

    <div class="panel">
      <h3 class="section-title">撞击事故记录（{selected.impacts.length}）</h3>
      <table>
        <thead><tr><th>时间</th><th>当时使用人</th><th>程度</th><th>地点</th></tr></thead>
        <tbody>
          {#each selected.impacts as e}
            <tr>
              <td class="small">{fmtDateTime(e.occurredAt)}</td>
              <td><strong>{e.holderName}</strong></td>
              <td class="small">{e.severityLabel}</td>
              <td class="small">{e.location || '—'}</td>
            </tr>
          {:else}
            <tr><td colspan="4" class="muted small">无撞击记录</td></tr>
          {/each}
        </tbody>
      </table>
    </div>
  </div>

  <div class="panel">
    <h3 class="section-title">安全员检查记录（{selected.inspections.length}）</h3>
    <table>
      <thead><tr><th>时间</th><th>安全员</th><th>外壳</th><th>内衬</th><th>结论</th><th>依据</th><th>附件</th></tr></thead>
      <tbody>
        {#each selected.inspections as ins}
          <tr>
            <td class="small">{fmtDateTime(ins.inspectedAt)}</td>
            <td>{ins.inspectorName}</td>
            <td class="small">{ins.shellLabel}</td>
            <td class="small">{ins.linerLabel}</td>
            <td>
              <span class="badge {ins.result === 'REUSE' ? 'IN_SERVICE' : ins.result === 'RETIRE' ? 'RETIRED' : 'FROZEN'}">
                {ins.resultLabel}
              </span>
              {#if ins.nextInspectionDate}<div class="small muted">下次 {fmtDate(ins.nextInspectionDate)}</div>{/if}
            </td>
            <td class="small" style="max-width:300px;">{ins.basis}</td>
            <td>
              {#each ins.attachments as a}
                <a class="small" href={a.downloadUrl} target="_blank">📎 {a.fileName}</a><br />
              {:else}<span class="muted small">无</span>{/each}
            </td>
          </tr>
        {:else}
          <tr><td colspan="7" class="muted small">暂无检查记录</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
