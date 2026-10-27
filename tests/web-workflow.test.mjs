import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
const workflow = readFileSync(new URL('../.github/workflows/pages.yml', import.meta.url), 'utf8');
const match = workflow.match(/node --input-type=module <<'JS'\n([\s\S]*?)\n {10}JS/);
assert.ok(match, 'Pages availability script exists');
const script = match[1].replace(/^ {10}/gm, '');
for (const status of [200, 404, 500]) {
  test(`Pages availability HTTP ${status}`, () => {
    const folder = mkdtempSync(join(tmpdir(), 'cmc-pages-test-'));
    const output = join(folder, 'output.txt');
    try {
      const runner = `globalThis.fetch = async () => ({status:${status}}); await import('data:text/javascript,' + encodeURIComponent(${JSON.stringify(script)}));`;
      const result = spawnSync(process.execPath, ['--input-type=module', '-e', runner], {
        env: { ...process.env, GITHUB_API_URL: 'https://api.github.com', GITHUB_REPOSITORY: 'example/example', GH_TOKEN: 'test-placeholder', GITHUB_OUTPUT: output },
        encoding: 'utf8',
      });
      if (status === 500) assert.notEqual(result.status, 0);
      else {
        assert.equal(result.status, 0, result.stderr);
        assert.equal(readFileSync(output, 'utf8'), `enabled=${status === 200}\n`);
        if (status === 404) assert.match(result.stdout, /deployment is skipped/);
      }
    } finally { rmSync(folder, {recursive:true,force:true}); }
  });
}
