import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [vue()],
    build: { emptyOutDir: false },
    server: {
      proxy: {
        '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8082', changeOrigin: true },
      },
    },
  };
});
