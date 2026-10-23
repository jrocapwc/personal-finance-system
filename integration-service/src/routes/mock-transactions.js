import { Router } from 'express';
import { requireInternalKey } from '../middleware/internal-auth.js';

export function mockTransactionRoutes(internalApiKey, provider) {
  const router = Router();
  router.get('/mock-transactions', requireInternalKey(internalApiKey), async (request, response) => {
    if (Object.keys(request.query).length > 0) {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'Query parameters are not supported' });
    }
    response.set('Cache-Control', 'no-store').json({ transactions: await provider() });
  });
  return router;
}
