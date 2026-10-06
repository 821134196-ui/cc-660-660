<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { fmtTime } from '../labels.js';

  let records = [];
  let error = '';

  onMount(async () => {
    try {
      records = await api.retirements();
    } catch (e) { error = e.message; }
  });
</script>

<div class="card">
  <h2>退役记录</h2>
  <p class="muted">退役为最终结论，系统不提供撤销入口；退役编号永久保留，扫码与重复登记均会被拦截。</p>
  {#if error}<div class="error-msg">{error}</div>{/if}
  <table>
    <thead><tr><th>编号</th><th>批次</th><th>退役时间</th><th>判定安全员</th><th>退役依据</th><th></th></tr></thead>
    <tbody>
      {#each records as r}
        <tr>
          <td><b>{r.helmet.code}</b></td>
          <td>{r.helmet.batch}</td>
          <td>{fmtTime(r.retiredAt)}</td>
          <td>{r.inspector}</td>
          <td>{r.reason}</td>
          <td><a href="#/helmets/{r.helmet.id}">档案</a></td>
        </tr>
      {/each}
      {#if !records.length}<tr><td colspan="6" class="muted">暂无退役记录</td></tr>{/if}
    </tbody>
  </table>
</div>
