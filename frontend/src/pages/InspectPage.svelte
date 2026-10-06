<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { SEVERITY, IMPACT_STATUS, DECISION, PART, fmtTime } from '../labels.js';

  export let eventId = null;

  let queue = [];
  let selected = null; // { event, inspections: [{inspection, attachments}] }
  let error = '';
  let notice = '';

  // 检查表单
  let inspector = '';
  let shellCondition = 'OK';
  let linerCondition = 'OK';
  let basis = '';
  let decision = 'RECHECK';
  let nextInspectionDate = '';
  let files = null;

  async function loadQueue(keepSelection = false) {
    error = '';
    try {
      const [pending, recheck] = await Promise.all([api.impacts('PENDING'), api.impacts('IN_RECHECK')]);
      queue = [...pending, ...recheck];
      if (!keepSelection) selected = null;
      if (eventId) {
        await select(eventId);
        eventId = null;
      }
    } catch (e) { error = e.message; }
  }

  onMount(loadQueue);

  async function select(id) {
    error = ''; notice = '';
    try {
      selected = await api.impact(id);
      inspector = ''; shellCondition = 'OK'; linerCondition = 'OK';
      basis = ''; decision = 'RECHECK'; nextInspectionDate = ''; files = null;
    } catch (e) { error = e.message; }
  }

  $: ev = selected?.event;
  $: helmet = ev?.helmet;
  $: expired = helmet?.serviceLifeExpired ?? false;
  $: unknownSeverity = ev?.severity === 'UNKNOWN';
  $: reuseBlocked = unknownSeverity || expired || shellCondition === 'DAMAGED' || linerCondition === 'DAMAGED';
  $: if (decision === 'REUSE' && reuseBlocked) decision = 'RECHECK';

  async function submit() {
    error = ''; notice = '';
    if (!inspector.trim()) { error = '请填写安全员姓名'; return; }
    if (!basis.trim()) { error = '请填写检查依据'; return; }
    if (decision === 'REUSE' && !nextInspectionDate) { error = '结论为复用时必须注明下次检查日期'; return; }
    const fd = new FormData();
    fd.append('inspector', inspector.trim());
    fd.append('shellCondition', shellCondition);
    fd.append('linerCondition', linerCondition);
    fd.append('basis', basis.trim());
    fd.append('decision', decision);
    if (decision === 'REUSE') fd.append('nextInspectionDate', nextInspectionDate);
    if (files) for (const f of files) fd.append('files', f);
    try {
      await api.inspect(ev.id, fd);
      const msg = { REUSE: '已判定复用，安全帽解冻回库', RECHECK: '已退回复检，安全帽保持冻结', RETIRE: '已判定退役，结论不可撤销' };
      notice = msg[decision];
      selected = null;
      await loadQueue();
    } catch (e) { error = e.message; }
  }
</script>

<div class="card">
  <h2>检查工作台</h2>
  {#if error}<div class="error-msg">{error}</div>{/if}
  {#if notice}<div class="ok-msg">{notice}</div>{/if}
  {#if !queue.length}
    <p class="muted">当前没有待检查或复检中的撞击事件。</p>
  {:else}
    <table>
      <thead><tr><th>事件</th><th>编号</th><th>批次</th><th>撞击程度</th><th>状态</th><th>当时使用人</th><th>上报时间</th><th></th></tr></thead>
      <tbody>
        {#each queue as q}
          <tr>
            <td>#{q.id}</td>
            <td><b>{q.helmet.code}</b></td>
            <td>{q.helmet.batch}</td>
            <td>
              {SEVERITY[q.severity]}
              {#if q.severity === 'UNKNOWN'}<span class="badge" style="background:#fef3c7;color:#b45309">不得直接复用</span>{/if}
            </td>
            <td><span class="badge" style="background:{q.status === 'PENDING' ? '#fee2e2' : '#fef3c7'};color:{q.status === 'PENDING' ? '#b91c1c' : '#b45309'}">{IMPACT_STATUS[q.status]}</span></td>
            <td>{q.userAtImpact ?? '库存中'}</td>
            <td>{fmtTime(q.reportedAt)}</td>
            <td><button class="secondary" on:click={() => select(q.id)}>检查</button></td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>

{#if selected && ev && helmet}
  <div class="card" style="border-left:8px solid #b45309">
    <h3>检查事件 #{ev.id} ｜ 安全帽 {helmet.code}（{IMPACT_STATUS[ev.status]}）</h3>
    <table>
      <tr><th style="width:140px">生产批次</th><td>{helmet.batch}</td></tr>
      <tr><th>生产/期限</th><td>{helmet.manufactureDate} 生产，{helmet.serviceLifeMonths} 个月，至 {helmet.serviceLifeEndDate}
        {#if expired}<span class="badge" style="background:#fee2e2;color:#b91c1c">已超期，必须退役</span>{/if}</td></tr>
      <tr><th>撞击程度</th><td>{SEVERITY[ev.severity]}
        {#if unknownSeverity}<span class="badge" style="background:#fef3c7;color:#b45309">程度不明，不能默认合格</span>{/if}</td></tr>
      <tr><th>当时使用人</th><td>{ev.userAtImpact ?? '库存中（无人领用）'}</td></tr>
      <tr><th>情况描述</th><td>{ev.description ?? '—'}</td></tr>
    </table>

    {#if selected.inspections.length}
      <h3 style="margin-top:16px">历史检查</h3>
      {#each selected.inspections as i}
        <p class="muted">
          第 {i.inspection.roundNo} 轮：{i.inspection.inspector} 判定「{DECISION[i.inspection.decision]}」，
          外壳{PART[i.inspection.shellCondition]}/内衬{PART[i.inspection.linerCondition]}，依据：{i.inspection.basis}
          （{fmtTime(i.inspection.inspectedAt)}）
        </p>
      {/each}
    {/if}

    <h3 style="margin-top:16px">填写第 {(selected.inspections.length || 0) + 1} 轮检查</h3>
    <div class="row" style="margin-bottom:10px">
      <label>安全员 <input bind:value={inspector} placeholder="姓名" /></label>
      <label>外壳
        <select bind:value={shellCondition}>
          <option value="OK">完好</option>
          <option value="DAMAGED">损伤</option>
        </select>
      </label>
      <label>内衬
        <select bind:value={linerCondition}>
          <option value="OK">完好</option>
          <option value="DAMAGED">损伤</option>
        </select>
      </label>
      <label>附件 <input type="file" multiple on:change={(e) => (files = e.target.files)} /></label>
    </div>
    <textarea style="width:100%" rows="3" placeholder="检查依据（必填）：检测方法、标准条款、观察结果等" bind:value={basis}></textarea>

    <div class="row" style="margin-top:12px">
      <label><input type="radio" bind:group={decision} value="REUSE" disabled={reuseBlocked} /> 复用</label>
      <label><input type="radio" bind:group={decision} value="RECHECK" /> 复检（保持冻结）</label>
      <label><input type="radio" bind:group={decision} value="RETIRE" /> 退役（不可撤销）</label>
    </div>
    {#if reuseBlocked}
      <p class="muted">
        不可复用原因：
        {#if unknownSeverity}撞击程度不明；{/if}
        {#if expired}已超使用年限；{/if}
        {#if shellCondition === 'DAMAGED' || linerCondition === 'DAMAGED'}外壳/内衬损伤。{/if}
      </p>
    {/if}
    {#if decision === 'REUSE'}
      <p><label>下次检查日期 <input type="date" bind:value={nextInspectionDate} /></label></p>
    {/if}
    {#if decision === 'RECHECK'}
      <p class="muted">退回复检后事件进入「复检中」，安全帽保持冻结，仍不能领用，需再次提交检查。</p>
    {/if}
    {#if decision === 'RETIRE'}
      <p class="error-msg">退役为最终结论，提交后不可撤销，该编号将永久禁止发放。</p>
    {/if}
    <button on:click={submit}>提交检查结论</button>
  </div>
{/if}
