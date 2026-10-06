<script>
  import { api, fileToBase64, fmtDate, fmtDateTime } from '../api.js';
  import { showToast } from '../toast.js';

  export let params;
  let code = params.get('code') || '';
  let detail = null;
  let loading = false;
  let busy = false;

  let form = {
    shellCondition: 'INTACT',
    linerCondition: 'INTACT',
    result: 'REUSE',
    basis: '',
    inspectorName: '',
    nextInspectionDate: ''
  };
  let files = [];

  const shells = [
    { value: 'INTACT', label: '外壳完好' },
    { value: 'WORN', label: '磨损/划痕' },
    { value: 'CRACKED', label: '裂纹/破裂/穿孔' }
  ];
  const liners = [
    { value: 'INTACT', label: '内衬完好' },
    { value: 'AGED', label: '老化/松弛' },
    { value: 'DAMAGED', label: '撕裂/脱开/缺失' }
  ];

  async function load() {
    if (!code.trim()) return;
    loading = true;
    try {
      detail = await api.detail(code.trim().toUpperCase());
      // 复检退回的帽子默认仍展示为冻结态，提示继续检查
    } catch (e) {
      detail = null;
      showToast(e.message);
    } finally {
      loading = false;
    }
  }
  if (code) load();

  async function onPick(e) {
    files = Array.from(e.target.files || []);
  }

  async function submit() {
    if (!detail) {
      showToast('请先查询要检查的安全帽');
      return;
    }
    if (!form.basis.trim()) {
      showToast('必须填写判定依据（规范条款或检查事实）');
      return;
    }
    if (!form.inspectorName.trim()) {
      showToast('必须填写安全员姓名');
      return;
    }
    if (form.result === 'REUSE' && !form.nextInspectionDate) {
      showToast('判定复用必须注明下次检查日');
      return;
    }
    busy = true;
    try {
      const attachments = [];
      for (const f of files) {
        attachments.push({
          fileName: f.name,
          contentType: f.type || 'application/octet-stream',
          contentBase64: await fileToBase64(f)
        });
      }
      const payload = {
        shellCondition: form.shellCondition,
        linerCondition: form.linerCondition,
        result: form.result,
        basis: form.basis.trim(),
        inspectorName: form.inspectorName.trim(),
        nextInspectionDate: form.result === 'REUSE' ? form.nextInspectionDate : null,
        attachments
      };
      await api.inspect(code.trim().toUpperCase(), payload);
      showToast('检查结论已提交', 'ok');
      files = [];
      form.basis = '';
      form.nextInspectionDate = '';
      await load();
    } catch (e) {
      showToast(e.message);
    } finally {
      busy = false;
    }
  }

  $: h = detail?.helmet;
  $: frozenLike = h && (h.status === 'FROZEN' || h.status === 'REINSPECTING');
</script>

<div class="panel">
  <h1>安全员检查判定</h1>
  <div class="scan-input" style="margin-bottom:8px;">
    <input class="mono" placeholder="输入/扫描待检安全帽编号" bind:value={code}
           on:keydown={(e) => e.key === 'Enter' && load()} />
    <button class="primary" on:click={load} disabled={loading}>查询待检帽</button>
  </div>
</div>

{#if h}
  <div class="panel">
    <span class="badge {h.status}">{h.statusLabel}</span>
    <span class="mono" style="margin-left:10px;font-size:18px;font-weight:700;">{h.code}</span>
    <span class="muted small" style="margin-left:10px;">批次 {h.batch} · 生产日期 {fmtDate(h.manufactureDate)}</span>

    {#if h.status === 'RETIRED'}
      <div class="callout stop" style="margin-top:14px;">
        该帽已退役（{fmtDateTime(h.retiredAt)}），退役结论不可更改、不可通过库存编辑撤销。
      </div>
    {:else if h.status === 'IN_SERVICE'}
      <div class="callout info" style="margin-top:14px;">该帽当前在役，未处于受冲击冻结状态，无需提交检查结论。</div>
    {:else}
      {#if h.status === 'REINSPECTING'}
        <div class="callout wait">该帽为<strong>复检退回</strong>状态，仓库仍按冻结处理、禁止领出。请完成进一步检验后再判定。</div>
      {:else}
        <div class="callout wait">该帽受冲击已冻结，请检查外壳、内衬及使用年限后给出结论。</div>
      {/if}

      {#if h.serviceLifeExpired}
        <div class="callout stop">系统按生产日期核算：已超过 3 年使用年限，不得判定复用，只能退役。</div>
      {/if}

      <div class="grid2">
        <div>
          <label>外壳检查 *</label>
          <select bind:value={form.shellCondition}>
            {#each shells as s}<option value={s.value}>{s.label}</option>{/each}
          </select>
        </div>
        <div>
          <label>内衬/悬挂系统 *</label>
          <select bind:value={form.linerCondition}>
            {#each liners as s}<option value={s.value}>{s.label}</option>{/each}
          </select>
        </div>
      </div>

      <label>检查结论 *</label>
      <div class="tabs" style="margin-top:4px;">
        <button class:active={form.result === 'REUSE'} on:click={() => (form.result = 'REUSE')}
                class="sm">✅ 判定复用</button>
        <button class:active={form.result === 'REINSPECT'} on:click={() => (form.result = 'REINSPECT')}
                class="sm">🔁 退回复检（保持冻结）</button>
        <button class:active={form.result === 'RETIRE'} on:click={() => (form.result = 'RETIRE')}
                class="sm danger">🚫 判定退役（不可逆）</button>
      </div>

      {#if form.result === 'REUSE'}
        <div class="callout go">复用条件：外壳无裂纹、内衬无破损、未超使用年限；并须确定下次检查日。</div>
        <label>下次检查日 *</label>
        <input type="date" bind:value={form.nextInspectionDate} />
      {:else if form.result === 'REINSPECT'}
        <div class="callout wait">退回复检后帽子<strong>继续冻结</strong>，仓库扫码仍拦截，直到复检结论为复用或退役。</div>
      {:else}
        <div class="callout stop">退役为终态结论：编号永久作废，旧码再次扫码领用或重新登记都会被拦截。</div>
      {/if}

      <label>判定依据 *（规范条款 / 检查事实）</label>
      <textarea bind:value={form.basis}
                placeholder="如：依据 GB 2811-2019，帽壳无明显变形与裂纹，衬带完整，距生产日期 20 个月未满 3 年；冲击吸收性能复测合格。"></textarea>

      <label>安全员姓名 *</label>
      <input bind:value={form.inspectorName} placeholder="如：赵建国" />

      <label>检查附件（照片/检测单，存本地，单个 ≤10MB，可多选）</label>
      <input type="file" multiple on:change={onPick} />
      {#if files.length}
        <div class="small muted" style="margin-top:6px;">
          {#each files as f}<span class="tag">📎 {f.name}（{(f.size / 1024).toFixed(0)} KB）</span>{/each}
        </div>
      {/if}

      <div style="margin-top:16px;">
        <button class="primary" on:click={submit} disabled={busy}>提交检查结论</button>
      </div>
    {/if}
  </div>

  {#if detail.inspections.length}
    <div class="panel">
      <h3 class="section-title">历史检查记录（{detail.inspections.length}）</h3>
      <table>
        <thead>
          <tr><th>时间</th><th>安全员</th><th>外壳</th><th>内衬</th><th>年限</th><th>结论</th><th>依据</th><th>附件</th></tr>
        </thead>
        <tbody>
          {#each detail.inspections as ins}
            <tr>
              <td class="small">{fmtDateTime(ins.inspectedAt)}</td>
              <td>{ins.inspectorName}</td>
              <td class="small">{ins.shellLabel}</td>
              <td class="small">{ins.linerLabel}</td>
              <td>{ins.serviceLifeExpired ? '已超期' : '正常'}</td>
              <td>
                <span class="badge {ins.result === 'REUSE' ? 'IN_SERVICE' : ins.result === 'RETIRE' ? 'RETIRED' : 'FROZEN'}">
                  {ins.resultLabel}
                </span>
                {#if ins.nextInspectionDate}<div class="small muted">下次检查 {fmtDate(ins.nextInspectionDate)}</div>{/if}
              </td>
              <td class="small" style="max-width:280px;">{ins.basis}</td>
              <td>
                {#each ins.attachments as a}
                  <a class="small" href={a.downloadUrl} target="_blank">📎 {a.fileName}</a><br />
                {:else}<span class="muted small">无</span>{/each}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
{/if}
