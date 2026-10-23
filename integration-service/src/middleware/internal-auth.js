import { createHash, timingSafeEqual } from 'node:crypto';

export function requireInternalKey(expected) {
  const expectedHash = createHash('sha256').update(expected).digest();
  return (request, response, next) => {
    const supplied = request.get('X-Internal-API-Key');
    if (typeof supplied !== 'string' || !timingSafeEqual(expectedHash, createHash('sha256').update(supplied).digest())) {
      return response.status(401).json({ code: 'UNAUTHORIZED', message: 'Missing or invalid internal API key' });
    }
    next();
  };
}
