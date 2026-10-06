<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { STATUS } from '../labels.js';

  let helmets = [];
  let filter = '';
  let error = '';
  let notice = '';

  // 登记表单
  let code = '';
  let batch = '';
  let manufactureDate = '';
  let serviceLifeMonths = 30;

  async function load() {
    error = '';
    try {
      helmets = await api.helmets(filter || null);
    } catch (e) { error = e.message; }
  }

  onMount(load);

  async function register() {
    error = ''; notice = '';
    try {
      await api.register({
        code: code.trim(),
        batch: batch.trim(),
        manufactureDate,
        serviceLifeMonths: Number(serviceLifeMonths)
      });
      notice = `编号 ${code.trim()} 登记成功`;
      code = ''; batch = ''; manufactureDate = ''; serviceLifeMonths = 30;
      await load();
    } catch (e) { error = e.message; }
  }
</script>

<div class="card">
  <h2>登记新安全帽</h2>
  <div class="row">
    <input placeholder="唯一编号，如 HMT-1008" bind:value={code} />
    <input placeholder="生产批次" bind:value={batch} />
    <label class="muted">生产日期 <input type="date" bind:value={manufactureDate} /></label>
    <label class="muted">使用期限（月） <input type="number" min="1" style="width:90px" bind:value={serviceLifeMonths} /></label>
    <button on:click={register}>登记</button>
  </div>
  <p class="muted">编号全局唯一：已退役的旧编号再次登记会被拦截。</p>
  {#if error}<div class="error-msg">{error}</div>{/if}
  {#if notice}<div class="ok-msg">{notice}</div>{/if}
</div>

<div class="card">
  <div class="row" style="justify-content:space-between">
    <h2>安全帽台账</h2>
    <label class="muted">状态筛选：
      <select bind:value={filter} on:change={load}>
        <option value="">全部</option>
        <option value="IN_STOCK">在库可领</option>
        <option value="ISSUED">已领出</option>
        <option value="FROZEN">冻结待检</option>
        <option value="RETIRED">已退役</option>
      </select>
    </label>
  </div>
  <table>
    <thead>
      <tr><th>编号</th><th>批次</th><th>生产日期</th><th>期限至</th><th>状态</th><th>下次检查</th><th></th></tr>
    </thead>
    <tbody>
      {#each helmets as h}
        <tr>
          <td><b>{h.code}</b></td>
          <td>{h.batch}</td>
          <td>{h.manufactureDate}</td>
          <td>
            {h.serviceLifeEndDate}
            {#if h.serviceLifeExpired}<span class="badge" style="background:#fee2e2;color:#b91c1c">超期</span>{/if}
          </td>
          <td>
            <span class="badge" style="background:{STATUS[h.status].bg};color:{STATUS[h.status].color}">
              {STATUS[h.status].label}
            </span>
          </td>
          <td>{h.nextInspectionDate ?? '—'}</td>
          <td><a href="#/helmets/{h.id}">详情</a></td>
        </tr>
      {/each}
      {#if !helmets.length}
        <tr><td colspan="7" class="muted">暂无数据</td></tr>
      {/if}
    </tbody>
  </table>
</div>
