import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { existsSync, readdirSync, mkdirSync, copyFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

// Web v1 决策：
// - 构建产物输出到 ../src/main/resources/static/web/（仍保留 web 子目录避免污染默认 static 根）
// - 浏览器访问入口：/（SPA 入口由 Spring WebStaticResourceConfig 把 / 与未知路径 forward 到 static/web/index.html）
// - assets 由 Spring ResourceHandler 把 /assets/** 映射回 classpath:/static/web/assets/
// - dev 模式 Vite 代理 /api 到本地后端 8080

// D1-W18 之后修复：Vite 的 emptyOutDir 会清掉 src/main/resources/static/web，
// 但 IntelliJ/Spring Boot DevTools 的增量同步可能不会把新产物完整复制到 target/classes/static/web，
// 造成本地后端启动后访问 / 报"静态资源未找到: index.html"。
// 这里加一个构建后钩子，把 src/main/resources/static/web/ 镜像到 target/classes/static/web/，
// 让正在跑的 Spring Boot（无论从 target/classes 还是 src/main/resources 加载）都能命中。
// 仅在 target 目录已经存在时才同步（避免 vite 在没跑过 mvn 的开发环境里凭空创建 target）。
function copyDirSync(src, dst) {
  if (!existsSync(src)) return
  mkdirSync(dst, { recursive: true })
  for (const entry of readdirSync(src, { withFileTypes: true })) {
    const s = join(src, entry.name)
    const d = join(dst, entry.name)
    if (entry.isDirectory()) copyDirSync(s, d)
    else copyFileSync(s, d)
  }
}
function syncToTargetClasses() {
  return {
    name: 'sync-to-target-classes',
    closeBundle() {
      // 此 vite.config.js 本身位于 thunder_note_server/frontend/，
      // new URL('.', import.meta.url) 已经指向 frontend/ 目录，无需再 dirname。
      const frontendRoot = fileURLToPath(new URL('.', import.meta.url))
      const serverRoot = resolve(frontendRoot, '..')
      const src = resolve(serverRoot, 'src/main/resources/static/web')
      const dst = resolve(serverRoot, 'target/classes/static/web')
      if (!existsSync(resolve(serverRoot, 'target/classes'))) {
        // 还没 mvn compile 过，跳过同步即可
        return
      }
      try {
        copyDirSync(src, dst)
        // eslint-disable-next-line no-console
        console.log('[vite] synced static/web → target/classes/static/web')
      } catch (e) {
        // eslint-disable-next-line no-console
        console.warn('[vite] failed to sync to target/classes:', e?.message || e)
      }
    }
  }
}

export default defineConfig({
  base: '/',
  plugins: [vue(), syncToTargetClasses()],
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
