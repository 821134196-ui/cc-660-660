<script>
  import { onMount } from 'svelte';
  import ScanPage from './pages/ScanPage.svelte';
  import HelmetsPage from './pages/HelmetsPage.svelte';
  import HelmetDetailPage from './pages/HelmetDetailPage.svelte';
  import InspectPage from './pages/InspectPage.svelte';
  import RetirementsPage from './pages/RetirementsPage.svelte';

  let route = { name: 'scan' };

  function parse() {
    const h = location.hash.replace(/^#/, '') || '/';
    const parts = h.split('/');
    if (parts[1] === 'helmets' && parts[2]) route = { name: 'detail', id: Number(parts[2]) };
    else if (parts[1] === 'helmets') route = { name: 'helmets' };
    else if (parts[1] === 'inspect') route = { name: 'inspect', eventId: parts[2] ? Number(parts[2]) : null };
    else if (parts[1] === 'retirements') route = { name: 'retirements' };
    else route = { name: 'scan' };
  }

  onMount(() => {
    parse();
    window.addEventListener('hashchange', parse);
    return () => window.removeEventListener('hashchange', parse);
  });

  const nav = [
    { href: '#/', label: '扫码收发', name: 'scan' },
    { href: '#/helmets', label: '安全帽台账', name: 'helmets' },
    { href: '#/inspect', label: '检查工作台', name: 'inspect' },
    { href: '#/retirements', label: '退役记录', name: 'retirements' }
  ];
</script>

<header>
  <div class="brand">🪖 安全帽受冲击退役判定系统</div>
  <nav>
    {#each nav as item}
      <a href={item.href} class:active={route.name === item.name}>{item.label}</a>
    {/each}
  </nav>
</header>

<main>
  {#if route.name === 'scan'}
    <ScanPage />
  {:else if route.name === 'helmets'}
    <HelmetsPage />
  {:else if route.name === 'detail'}
    <HelmetDetailPage id={route.id} />
  {:else if route.name === 'inspect'}
    <InspectPage eventId={route.eventId} />
  {:else if route.name === 'retirements'}
    <RetirementsPage />
  {/if}
</main>

<style>
  :global(*) { box-sizing: border-box; }
  :global(body) {
    margin: 0;
    font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
    background: #f1f5f9;
    color: #1e293b;
  }
  header {
    background: #0f172a;
    color: #fff;
    padding: 0 24px;
    display: flex;
    align-items: center;
    gap: 32px;
    flex-wrap: wrap;
  }
  .brand { font-size: 18px; font-weight: 700; padding: 14px 0; }
  nav { display: flex; gap: 4px; }
  nav a {
    color: #94a3b8;
    text-decoration: none;
    padding: 14px 16px;
    font-size: 15px;
    border-bottom: 3px solid transparent;
  }
  nav a.active { color: #fff; border-bottom-color: #38bdf8; }
  nav a:hover { color: #e2e8f0; }
  main { max-width: 1080px; margin: 24px auto; padding: 0 16px; }

  :global(.card) {
    background: #fff;
    border-radius: 10px;
    padding: 20px;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
    margin-bottom: 16px;
  }
  :global(h2) { margin: 0 0 16px; font-size: 18px; }
  :global(h3) { margin: 0 0 12px; font-size: 16px; }
  :global(table) { width: 100%; border-collapse: collapse; font-size: 14px; }
  :global(th), :global(td) { text-align: left; padding: 8px 10px; border-bottom: 1px solid #e2e8f0; }
  :global(th) { color: #64748b; font-weight: 600; background: #f8fafc; }
  :global(input), :global(select), :global(textarea) {
    padding: 8px 10px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    font-size: 14px;
    font-family: inherit;
  }
  :global(input:focus), :global(select:focus), :global(textarea:focus) {
    outline: 2px solid #38bdf8;
    outline-offset: -1px;
  }
  :global(button) {
    padding: 8px 18px;
    border: none;
    border-radius: 6px;
    background: #0ea5e9;
    color: #fff;
    font-size: 14px;
    cursor: pointer;
    font-family: inherit;
  }
  :global(button:hover) { background: #0284c7; }
  :global(button:disabled) { background: #94a3b8; cursor: not-allowed; }
  :global(button.secondary) { background: #e2e8f0; color: #334155; }
  :global(button.secondary:hover) { background: #cbd5e1; }
  :global(button.danger) { background: #dc2626; }
  :global(button.danger:hover) { background: #b91c1c; }
  :global(.badge) {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 999px;
    font-size: 13px;
    font-weight: 600;
  }
  :global(.error-msg) {
    background: #fee2e2;
    color: #b91c1c;
    padding: 10px 14px;
    border-radius: 6px;
    margin: 10px 0;
    font-size: 14px;
  }
  :global(.ok-msg) {
    background: #dcfce7;
    color: #15803d;
    padding: 10px 14px;
    border-radius: 6px;
    margin: 10px 0;
    font-size: 14px;
  }
  :global(.muted) { color: #64748b; font-size: 13px; }
  :global(.row) { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
</style>
