import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { calculate, normalizeKey, hash33, javaAbsolute, secondsToNextCode } from '../site/otp.mjs';
const fixtures = JSON.parse(readFileSync(new URL('./web-otp-vectors.json', import.meta.url)));

test('fixtures describe the current app algorithm', () => {
  const source = readFileSync(new URL('../' + fixtures.source, import.meta.url));
  assert.equal(createHash('sha256').update(source).digest('hex'), fixtures.source_sha256);
});
test('all 136 native Java vectors match, including UTC boundaries', () => {
  assert.equal(fixtures.vectors.length, 136);
  for (const vector of fixtures.vectors) assert.equal(calculate(vector.key, vector.epochMillis).code, vector.code);
});
test('manual input normalization', () => {
  assert.equal(normalizeKey(' 1ABCD '), '1abcd');
  assert.equal(normalizeKey('I1234'), 'i1234');
});
test('invalid keys are rejected without silent truncation', () => {
  for (const key of [null, undefined, '', 'abcd', 'abcdef', 'a bcd', '가나다라마', 'ＡＢＣＤＥ', 'KEY:1abcd', 'a;123']) {
    assert.throws(() => calculate(key), /5자리/);
  }
});
test('signed 32-bit Java hashing and MIN_VALUE overflow', () => {
  assert.equal(hash33('a'), 97);
  assert.equal(hash33('1abcd2610172700'), -1764156171);
  assert.equal(javaAbsolute(-2147483648), -2147483648);
  assert.equal(javaAbsolute(-1), 1);
  assert.equal(javaAbsolute(0), 0);
});
test('current minute, second countdown and next minute', () => {
  const time = Date.parse('2026-10-27T00:17:00Z');
  assert.equal(calculate('1abcd', time).code, '1764156171');
  assert.equal(calculate('1abcd', time + 59999).code, '1764156171');
  assert.equal(calculate('1abcd', time + 60000).code, '1762970250');
  assert.equal(calculate('1abcd', time).utcMinute, '2026-10-27 00:17 UTC');
  for (const [delta, expected] of [[0,60],[1000,59],[59000,1],[59999,1],[60000,60]]) assert.equal(secondsToNextCode(time + delta), expected);
  assert.equal(secondsToNextCode(-1), 1);
});
test('local timezone does not affect UTC calculation', () => {
  const previous = process.env.TZ;
  try {
    for (const zone of ['Asia/Seoul', 'Pacific/Honolulu', 'Europe/London']) {
      process.env.TZ = zone;
      assert.equal(calculate('1abcd', Date.parse('2026-10-27T00:17:00Z')).code, '1764156171');
    }
  } finally { if (previous === undefined) delete process.env.TZ; else process.env.TZ = previous; }
});
test('invalid device time is rejected', () => assert.throws(() => calculate('1abcd', NaN), /시간/));
