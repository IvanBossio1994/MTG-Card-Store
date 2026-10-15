/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * Configuración de Vite: la app se sirve en /app desde Spring y el build queda en
 * target/classes/static/app (lo empaqueta Maven en el .jar).
 * Ejemplo: `npm run build` → ../target/classes/static/app/index.html
 */
export default defineConfig({
  base: '/app/',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  build: {
    outDir: '../target/classes/static/app',
    emptyOutDir: true,
  },
  server: {
    // En desarrollo (npm run dev) la API y la mascota las sirve Spring en el 8080.
    proxy: {
      '/api': 'http://localhost:8080',
      '/images': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/compartido/preparacionTests.ts'],
    css: false,
  },
});
