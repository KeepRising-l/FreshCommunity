import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    // 拆包：vue 全家桶与 Element Plus 独立 chunk——两者变更频率远低于业务代码，
    // 拆出后业务迭代不失效大库缓存，主包从 1.3MB 降到百 KB 级
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 后端静态服务的用户上传图片（头像/提货点照片/商品图），开发态同样代理
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
