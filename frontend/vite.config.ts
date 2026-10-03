import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/shows': 'http://localhost:8080',
      '/reservations': 'http://localhost:8080',
      '/event-requests': 'http://localhost:8080',
      '/health': 'http://localhost:8080',
      '/metrics': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
