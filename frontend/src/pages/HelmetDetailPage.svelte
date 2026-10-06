<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { STATUS, SEVERITY, IMPACT_STATUS, DECISION, PART, fmtTime } from '../labels.js';

  export let id;

  let detail = null;
  let error = '';

  onMount(async () => {
    try {
      detail = await api.helmet(id);
    } catch (e) { error = e.message; }
  });
</script>

{#if error}<div class="error-msg">{error}</div>{/if}

{#if detail}
  {@const h = detail.helmet}
  {@const st = STATUS[h.status]}
  <div class="card" style="border-left:8px solid {st.color}">
    <div class="row" style="justify-content:space-between">
      <h2 style="margin:0">{h.code}</h2>
      <span class="badge" style="background:{st.bg};color:{st.color};font-size:16px;padding:6px 18px">{st.label}</span>
    </div>
    <table style="margin-top:12px">
      <tr><th style="width:140px">生产批次</th><td>{h.batch}</td></tr>
      <tr><th>生产日期</th><td>{h.manufactureDate}</td></tr>
      <tr><th>使用期限</th><td>{h.serviceLifeMonths} 个月（至 {h.serviceLifeEndDate}）
        {#if h.serviceLifeExpired}<span class="badge" style="background:#fee2e2;color:#b91c1c">已超期</span>{/if}</td></tr>
      {#if detail.currentHolder}<tr><th>当前领用人</th><td><b>{detail.currentHolder}</b></td></tr>{/if}
      {#if h.nextInspectionDate}<tr><th>下次检查日期</th><td><b>{h.nextInspectionDate}</b></td></tr>{/if}
      {#if h.status === 'RETIRED'}
        <tr><th>退役时间</th><td>{fmtTime(h.retiredAt)}</td></tr>
        <tr><th>退役依据</th><td>{h.retireReason}</td></tr>
      {/if}
    </table>
  </div>

  <div class="card">
    <h3>领用历史</h3>
    <table>
      <thead><tr><th>领用人</th><th>领用时间</th><th>归还时间</th><th>备注</th></tr></thead>
      <tbody>
        {#each detail.issues as r}
          <tr>
            <td>{r.workerName}</td>
            <td>{fmtTime(r.issuedAt)}</td>
            <td>{r.returnedAt ? fmtTime(r.returnedAt) : '在用'}</td>
            <td class="muted">{r.returnNote ?? ''}</td>
          </tr>
        {/each}
        {#if !detail.issues.length}<tr><td colspan="4" class="muted">暂无领用记录</td></tr>{/if}
      </tbody>
    </table>
  </div>

  <div class="card">
    <h3>撞击事件与检查记录</h3>
    {#each detail.events as e}
      <div class="card" style="background:#f8fafc">
        <div class="row" style="justify-content:space-between">
          <b>事件 #{e.event.id} ｜ {SEVERITY[e.event.severity]} ｜ {IMPACT_STATUS[e.event.status]}</b>
          <span class="muted">{fmtTime(e.event.reportedAt)}</span>
        </div>
        <table style="margin-top:8px">
          <tr><th style="width:140px">当时使用人</th><td>{e.event.userAtImpact ?? '库存中（无人领用）'}</td></tr>
          <tr><th>情况描述</th><td>{e.event.description ?? '—'}</td></tr>
          {#if e.event.finalDecision}
            <tr><th>最终结论</th><td>{DECISION[e.event.finalDecision]}（{fmtTime(e.event.closedAt)}）</td></tr>
          {/if}
        </table>

        {#each e.inspections as i}
          <div class="card" style="background:#fff;border:1px solid #e2e8f0;margin-top:10px">
            <div class="row" style="justify-content:space-between">
              <b>第 {i.inspection.roundNo} 轮检查 ｜ 结论：{DECISION[i.inspection.decision]}</b>
              <span class="muted">{i.inspection.inspector} ｜ {fmtTime(i.inspection.inspectedAt)}</span>
            </div>
            <table style="margin-top:8px">
              <tr>
                <th style="width:140px">外壳 / 内衬</th>
                <td>{PART[i.inspection.shellCondition]} / {PART[i.inspection.linerCondition]}</td>
              </tr>
              <tr><th>使用年限</th><td>{i.inspection.serviceLifeOk ? '在期限内' : '已超期'}</td></tr>
              <tr><th>检查依据</th><td>{i.inspection.basis}</td></tr>
              {#if i.inspection.nextInspectionDate}
                <tr><th>下次检查日期</th><td>{i.inspection.nextInspectionDate}</td></tr>
              {/if}
              {#if i.attachments.length}
                <tr>
                  <th>附件</th>
                  <td>
                    {#each i.attachments as a}
                      <a href={api.attachmentUrl(a.id)}>{a.originalName}</a>&nbsp;
                    {/each}
                  </td>
                </tr>
              {/if}
            </table>
          </div>
        {/each}
        {#if e.event.status !== 'CLOSED'}
          <p><a href="#/inspect/{e.event.id}">→ 前往检查工作台处理</a></p>
        {/if}
      </div>
    {/each}
    {#if !detail.events.length}<p class="muted">暂无撞击事件</p>{/if}
  </div>
{/if}
