import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  server: {
    // 开发模式：代理到本地 Quarkus
    proxy: { '/api': 'http://localhost:8080' }
  },
  build: {
    outDir: 'dist'
  }
});
