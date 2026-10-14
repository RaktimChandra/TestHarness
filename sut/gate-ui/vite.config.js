import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The bundle is written into the Maven module's classes as META-INF/resources, which
// Spring Boot serves as static content when this jar is on gate-api's classpath.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'target/classes/META-INF/resources',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    // Pact runs one mock server at a time; keep contract tests in a single worker.
    fileParallelism: false,
  },
});
