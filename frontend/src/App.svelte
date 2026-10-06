<script>
  import { onMount } from 'svelte';
  import ScanPage from './pages/ScanPage.svelte';
  import ImpactPage from './pages/ImpactPage.svelte';
  import InspectPage from './pages/InspectPage.svelte';
  import InventoryPage from './pages/InventoryPage.svelte';
  import { toast } from './toast.js';

  // 简单 hash 路由：#/scan、#/impact、#/inspect、#/inventory
  const routes = {
    '#/scan': { title: '仓库扫码', component: ScanPage },
    '#/impact': { title: '撞击上报', component: ImpactPage },
    '#/inspect': { title: '安全员检查', component: InspectPage },
    '#/inventory': { title: '库存档案', component: InventoryPage }
  };

  let hash = location.hash || '#/scan';
  function onHash() {
    hash = location.hash || '#/scan';
    if (!routes[hash]) {
      const base = hash.split('?')[0];
      if (!routes[base]) hash = '#/scan';
    }
  }
  onMount(() => window.addEventListener('hashchange', onHash));

  $: routeKey = (hash || '#/scan').split('?')[0];
  $: route = routes[routeKey] || routes['#/scan'];
  $: params = new URLSearchParams((hash.split('?')[1] || ''));
  $: Page = route.component;
</script>

<div class="topbar">
  <div class="topbar-inner">
    <span class="brand">⛑ 安全帽冲击退役判定系统</span>
    <nav class="nav">
      <a href="#/scan" class:active={routeKey === '#/scan'}>仓库扫码</a>
      <a href="#/impact" class:active={routeKey === '#/impact'}>撞击上报</a>
      <a href="#/inspect" class:active={routeKey === '#/inspect'}>安全员检查</a>
      <a href="#/inventory" class:active={routeKey === '#/inventory'}>库存档案</a>
    </nav>
  </div>
</div>

<main class="layout">
  <svelte:component this={Page} {params} />
</main>

{#if $toast}
  <div class={$toast.kind === 'ok' ? 'toast-ok' : 'toast-error'}>{$toast.message}</div>
{/if}
