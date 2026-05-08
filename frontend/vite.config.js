import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Web v1 决策：
// - 构建产物输出到 ../src/main/resources/static/web/（仍保留 web 子目录避免污染默认 static 根）
// - 浏览器访问入口：/（SPA 入口由 Spring WebStaticResourceConfig 把 / 与未知路径 forward 到 static/web/index.html）
// - assets 由 Spring ResourceHandler 把 /assets/** 映射回 classpath:/static/web/assets/
// - dev 模式 Vite 代理 /api 到本地后端 8080
export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: '../src/main/resources/static/web',
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false
      }
    }
  }
})
