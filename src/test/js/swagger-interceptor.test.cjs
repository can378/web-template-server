const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/swagger/request-interceptor.js'), 'utf8');

function setup() {
  const calls = [];
  const interceptor = vm.runInNewContext(`(${source})`, {
    URL,
    window: { location: { href: 'http://localhost:8100/swagger-ui/index.html', origin: 'http://localhost:8100' } },
    fetch: async (url, options) => {
      calls.push({ url, options });
      return { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: `token-${calls.length}` }) };
    },
  });
  return { interceptor, calls };
}

test('configuration and specification requests can omit the HTTP method', async () => {
  const { interceptor, calls } = setup();
  for (const url of ['/v3/api-docs/swagger-config', '/v3/api-docs']) {
    const request = { url };
    assert.equal(await interceptor(request), request);
  }
  assert.equal(calls.length, 0);
});

test('mutations fetch fresh CSRF tokens; GET and other origins do not', async () => {
  const { interceptor, calls } = setup();
  const first = await interceptor({ url: '/api/auth/login', method: 'post' });
  const second = await interceptor({ url: '/api/users/me', method: 'PATCH' });
  assert.equal(first.headers['X-CSRF-TOKEN'], 'token-1');
  assert.equal(second.headers['X-CSRF-TOKEN'], 'token-2');
  assert.equal(calls[0].url, 'http://localhost:8100/api/auth/csrf');
  await interceptor({ url: '/api/users/me', method: 'GET' });
  const external = await interceptor({ url: 'https://example.com/api/users', method: 'POST' });
  assert.equal(external.headers, undefined);
  assert.equal(calls.length, 2);
});
