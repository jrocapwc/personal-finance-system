export function loadConfig(environment = process.env) {
  const internalApiKey = environment.INTEGRATION_INTERNAL_API_KEY ?? '';
  if (Buffer.byteLength(internalApiKey, 'utf8') < 32 || /[\r\n]/.test(internalApiKey)) {
    throw new Error('INTEGRATION_INTERNAL_API_KEY must contain at least 32 bytes without line breaks');
  }
  const rawPort = environment.PORT ?? '3001';
  if (!/^\d{1,5}$/.test(rawPort) || Number(rawPort) < 1 || Number(rawPort) > 65535) {
    throw new Error('PORT must be an integer between 1 and 65535');
  }
  return { internalApiKey, port: Number(rawPort) };
}
