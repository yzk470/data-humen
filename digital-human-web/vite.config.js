import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  build: {
    minify: false
  },
  resolve: {
    alias: {
      '@framework': path.resolve(__dirname, 'src/cubism-framework')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9091',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:9091',
        ws: true
      }
    }
  }
})
