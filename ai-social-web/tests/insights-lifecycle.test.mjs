import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const source = await readFile(
  new URL('../src/composables/useInsights.ts', import.meta.url),
  'utf8',
);
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;

function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function mountInsights(request) {
  let mount,
    unmount,
    created = 0;
  const timers = new Map();
  const exports = {};
  runInNewContext(js, {
    exports,
    URLSearchParams,
    require: (name) => {
      if (name === 'vue')
        return {
          ref: (value) => ({ value }),
          computed: (get) => ({
            get value() {
              return get();
            },
          }),
          onMounted: (callback) => {
            mount = callback;
          },
          onUnmounted: (callback) => {
            unmount = callback;
          },
        };
      if (name === '../services/http') return { IS_DEMO_MODE: false, request };
      if (name === '../lib/demo') return {};
      throw new Error(`未预期的依赖：${name}`);
    },
    setTimeout: (callback) => {
      const id = ++created;
      timers.set(id, callback);
      return id;
    },
    clearTimeout: (id) => timers.delete(id),
  });
  exports.useInsights();
  return { mount: () => mount(), unmount: () => unmount(), timers, created: () => created };
}

const page = { items: [], has_more: false, next_cursor: null };

test('AI 活动首次加载完成前离开页面，不会在卸载后启动轮询', async () => {
  const response = deferred();
  let calls = 0;
  const view = mountInsights(() => {
    calls++;
    return response.promise;
  });
  const loading = view.mount();
  view.unmount();
  response.resolve(page);
  await loading;
  assert.equal(calls, 1);
  assert.equal(view.created(), 0);
  assert.equal(view.timers.size, 0);
});

test('AI 活动轮询请求期间离开页面，不会重新注册定时器', async () => {
  const response = deferred();
  let calls = 0;
  const view = mountInsights(() =>
    ++calls === 1
      ? Promise.resolve({ ...page, items: [{ public_id: 'job', status: 'PROCESSING' }] })
      : response.promise,
  );
  await view.mount();
  const [id, callback] = view.timers.entries().next().value;
  view.timers.delete(id);
  const polling = callback();
  view.unmount();
  response.resolve(page);
  await polling;
  assert.equal(calls, 2);
  assert.equal(view.created(), 1);
  assert.equal(view.timers.size, 0);
});
