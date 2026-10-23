import { createApp } from './app.js';
import { loadConfig } from './config.js';

try {
  const { internalApiKey, port } = loadConfig();
  const server = createApp({ internalApiKey }).listen(port, '127.0.0.1', () => {
    console.info(`Mock integration service listening on http://127.0.0.1:${port}`);
  });
  server.on('error', () => { console.error('Integration service failed to listen'); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.once(signal, () => {
      server.close(() => { process.exitCode = 0; });
      setTimeout(() => server.closeAllConnections(), 5000).unref();
    });
  }
} catch {
  console.error('Invalid integration service configuration; check the documented environment variables');
  process.exitCode = 1;
}
