import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// 构建产物直接写入后端静态资源目录，由 Quarkus 统一在 8080 端口提供
export default defineConfig({
  plugins: [svelte()],
  build: {
    outDir: '../backend/src/main/resources/META-INF/resources',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
