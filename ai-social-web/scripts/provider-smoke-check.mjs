import { readFile, writeFile } from 'node:fs/promises';
const source = await readFile(
  new URL('../../ai-social-app/src/main/resources/application.yaml', import.meta.url),
  'utf8',
);
// Read the scalar provider settings without serializing any secrets.
const values = new Map(),
  stack = [];
for (const line of source.split(/\r?\n/)) {
  const match = /^( *)([\w-]+):\s*(.*?)\s*$/.exec(line);
  if (!match) continue;
  const depth = match[1].length;
  while (stack.length && stack.at(-1).depth >= depth) stack.pop();
  const path = [...stack.map((item) => item.key), match[2]].join('.');
  if (match[3] && !match[3].startsWith('#'))
    values.set(
      path,
      match[3]
        .replace(/\s+#.*$/, '')
        .trim()
        .replace(/^['"]|['"]$/g, ''),
    );
  else stack.push({ depth, key: match[2] });
}
function resolve(value) {
  if (!value) return '';
  const placeholder = /^\$\{([^:}]+)(?::([\s\S]*))?\}$/.exec(value);
  return placeholder ? process.env[placeholder[1]] || placeholder[2] || '' : value;
}
const url = resolve(values.get('ai.providers.dashscope.url'));
const key = resolve(values.get('ai.providers.dashscope.api-key'));
const endpoint = resolve(values.get('ai.providers.dashscope.endpoints.chat'));
const model = process.env.PROBE_MODEL || 'qwen-plus-latest';
let report;
if (!url || !key || !endpoint || key.includes('${'))
  report = {
    pass: false,
    reason: 'Provider configuration is not resolved; no external request sent',
  };
else {
  const started = Date.now();
  try {
    const response = await fetch(url.replace(/\/$/, '') + '/' + endpoint.replace(/^\//, ''), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + key },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: '请只回复 OK' }],
        max_tokens: 16,
        stream: false,
      }),
      signal: AbortSignal.timeout(45000),
    });
    const result = await response.json().catch(() => ({}));
    report = {
      timestamp: new Date().toISOString(),
      model,
      http_status: response.status,
      elapsed_ms: Date.now() - started,
      pass: response.ok && !!result.choices?.[0]?.message?.content,
      error: response.ok
        ? undefined
        : String(result.error?.code || result.code || 'provider error').replaceAll(
            key,
            '[redacted]',
          ),
    };
  } catch (e) {
    report = {
      timestamp: new Date().toISOString(),
      model,
      pass: false,
      error: e.name,
      elapsed_ms: Date.now() - started,
    };
  }
}
await writeFile(
  new URL('../../doc/provider-probe-evidence.json', import.meta.url),
  JSON.stringify(report, null, 2),
);
console.log(JSON.stringify(report));
if (!report.pass) process.exitCode = 1;
