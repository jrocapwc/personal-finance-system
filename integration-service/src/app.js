import express from 'express';
import { loadConfig } from './config.js';
import { mockTransactionRoutes } from './routes/mock-transactions.js';
import { getMockTransactions } from './services/mock-provider.js';
import { InvalidProviderRecord } from './services/normalizer.js';

export function createApp({ internalApiKey = loadConfig().internalApiKey, provider = getMockTransactions } = {}) {
  // Validate injected configuration too; never start with an empty development credential.
  loadConfig({ INTEGRATION_INTERNAL_API_KEY: internalApiKey });
  const app = express();
  app.disable('x-powered-by');
  app.get('/health', (_request, response) => response.json({ status: 'healthy' }));
  app.use('/internal', mockTransactionRoutes(internalApiKey, provider));
  app.use((_request, response) => response.status(404).json({ code: 'NOT_FOUND', message: 'Resource not found' }));
  app.use((error, _request, response, _next) => {
    const invalid = error instanceof InvalidProviderRecord;
    // Never log keys, exception text, or transaction payloads.
    response.status(invalid ? 502 : 500).json({ code: invalid ? 'INVALID_PROVIDER_RECORD' : 'INTERNAL_ERROR',
      message: invalid ? 'Mock provider returned an invalid transaction' : 'Unexpected service error' });
  });
  return app;
}
