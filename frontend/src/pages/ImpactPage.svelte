<script>
  import { api } from '../api.js';
  import { showToast } from '../toast.js';

  // 支持从扫码页跳转带入 ?code=
  export let params;
  let code = params.get('code') || '';
  let form = {
    severity: 'UNKNOWN',
    holderName: '',
    location: '',
    description: '',
    occurredAt: ''
  };
  let busy = false;
  let done = null;

  const severities = [
    { value: 'HEAVY', label: '重击（明显坠落/重物击打）' },
    { value: 'LIGHT', label: '轻微撞击' },
    { value: 'UNKNOWN', label: '程度不明（默认冻结，不默认合格）' }
  ];

  async function submit() {
    if (!code.trim()) {
      showToast('请输入受冲击安全帽编号');
      return;
    }
    if (!form.holderName.trim()) {
      showToast('必须填写撞击时使用人，事故记录须可追溯到人');
      return;
    }
    busy = true;
    try {
      const payload = {
        severity: form.severity,
        holderName: form.holderName.trim(),
        location: form.location.trim(),
        description: form.description.trim(),
        // datetime-local 为 "YYYY-MM-DDTHH:MM"，补秒成 Jackson 可解析的 ISO 格式
        occurredAt: form.occurredAt ? form.occurredAt + ':00' : null
      };
      done = await api.impact(code.trim().toUpperCase(), payload);
      showToast('已登记撞击事件，安全帽立即冻结，仓库已暂停发放', 'ok');
    } catch (e) {
      showToast(e.message);
    } finally {
      busy = false;
    }
  }
</script>

<div class="panel">
  <h1>受冲击上报（即时冻结）</h1>
  <p class="muted small">
    安全帽发生撞击后先在此登记，系统<strong>立即冻结</strong>该编号，仓库扫码将无法领出，
    随后由安全员检查外壳、内衬与使用年限。撞击程度不明时一律按冻结处理。
  </p>

  <label>安全帽编号 *</label>
  <input class="mono" bind:value={code} placeholder="如 AQM-1003" />

  <label>撞击程度 *</label>
  <select bind:value={form.severity}>
    {#each severities as s}
      <option value={s.value}>{s.label}</option>
    {/each}
  </select>

  <div class="grid2">
    <div>
      <label>撞击时使用人 *</label>
      <input bind:value={form.holderName} placeholder="事故记录将永久关联此人" />
    </div>
    <div>
      <label>发生地点</label>
      <input bind:value={form.location} placeholder="如：冲压车间 B 区" />
    </div>
  </div>

  <label>发生时间（不填默认当前时间）</label>
  <input type="datetime-local" bind:value={form.occurredAt} />

  <label>撞击经过描述</label>
  <textarea bind:value={form.description} placeholder="撞击物、高度、在场情况等"></textarea>

  <div style="margin-top:16px;">
    <button class="warn" on:click={submit} disabled={busy}>⚠ 登记撞击并冻结</button>
  </div>
</div>

{#if done}
  <div class="panel">
    <div class="callout wait">
      编号 {code.toUpperCase()} 已冻结。下一步：
      <a href="#/inspect?code={code.toUpperCase()}">通知安全员检查 →</a>
      &nbsp;或&nbsp;<a href="#/scan">去仓库扫码页确认状态</a>
    </div>
    <dl class="kv">
      <dt>事故编号</dt><dd>{done.id}</dd>
      <dt>当时使用人</dt><dd><strong>{done.holderName}</strong></dd>
      <dt>撞击程度</dt><dd>{done.severityLabel}</dd>
      <dt>发生时间</dt><dd>{done.occurredAt.replace('T', ' ').slice(0, 16)}</dd>
    </dl>
  </div>
{/if}
