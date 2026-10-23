// Validate embedded JavaScript without sending requests or evaluating scripts/credentials.
import { readdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { Script } from 'node:vm';

const root = fileURLToPath(new URL('../', import.meta.url));
let count = 0;
for (const service of ['core-api', 'ai-service', 'integration-service']) {
  const directory = resolve(root, service, 'bruno');
  for (const entry of await readdir(directory, { recursive: true, withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.bru')) continue;
    const file = resolve(entry.parentPath, entry.name);
    const source = await readFile(file, 'utf8');
    const blocks = source.matchAll(/^(?:script:[\w-]+|tests)\s*\{\r?\n([\s\S]*?)^\}/gm);
    for (const match of blocks) {
      new Script(match[1], { filename: file });
      count++;
    }
  }
}
console.info(`Validated ${count} embedded Bruno JavaScript blocks without execution.`);
