import { calculate, normalizeKey, secondsToNextCode } from './otp.mjs';
const $ = id => document.getElementById(id);
const input = $('key-input');
const code = $('code');
const copy = $('copy');
let result = null;
let timer = null;
function message(text = '') { $('copy-status').textContent = text; }
function clearResult() {
  result = null;
  clearTimeout(timer);
  timer = null;
  code.textContent = '—';
  code.classList.add('empty');
  code.setAttribute('aria-label', '아직 계산한 인증코드가 없습니다');
  $('result-hint').textContent = 'KEY를 입력한 뒤 계산을 눌러 주세요.';
  $('normalized-key').textContent = '';
  $('utc-time').textContent = '기준 시간은 UTC로 표시됩니다.';
  $('countdown-box').hidden = true;
  copy.disabled = true;
  message();
}
function render(computed) {
  result = computed;
  code.textContent = computed.code;
  code.classList.remove('empty');
  code.setAttribute('aria-label', `인증코드 ${[...computed.code].join(' ')}`);
  $('result-hint').textContent = '삼성 CMC 인증창에 입력할 코드';
  $('normalized-key').textContent = `KEY ${computed.key}`;
  $('utc-time').textContent = computed.utcMinute;
  $('countdown-box').hidden = false;
  copy.disabled = false;
  tick();
}
function tick() {
  clearTimeout(timer);
  timer = null;
  if (!result || document.hidden) return;
  const now = Date.now();
  if (result.minute !== Math.floor(now / 60000)) {
    result = calculate(result.key, now);
    code.textContent = result.code;
    code.setAttribute('aria-label', `인증코드 ${[...result.code].join(' ')}`);
    $('utc-time').textContent = result.utcMinute;
    message();
  }
  $('countdown').textContent = `다음 갱신까지 ${secondsToNextCode(now)}초`;
  timer = setTimeout(tick, 1000 - (now % 1000));
}
$('key-form').addEventListener('submit', event => {
  event.preventDefault();
  message();
  try {
    render(calculate(input.value));
    $('validation').hidden = true;
    input.removeAttribute('aria-invalid');
  } catch (error) {
    clearResult();
    $('validation').textContent = error.message;
    $('validation').hidden = false;
    input.setAttribute('aria-invalid', 'true');
    input.focus();
  }
});
input.addEventListener('input', () => {
  $('validation').hidden = true;
  input.removeAttribute('aria-invalid');
  if (result) {
    try { if (normalizeKey(input.value) === result.key) return; } catch { /* Invalidate below. */ }
  }
  clearResult();
});
$('clear').addEventListener('click', () => {
  input.value = '';
  input.removeAttribute('aria-invalid');
  $('validation').hidden = true;
  clearResult();
  input.focus();
});
copy.addEventListener('click', async () => {
  if (!result) return;
  let computed;
  try {
    const key = normalizeKey(input.value);
    if (key !== result.key) { clearResult(); return; }
    computed = calculate(key); // Recompute at the tap, including a minute boundary.
    render(computed);
  } catch { clearResult(); return; }
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
    await navigator.clipboard.writeText(computed.code);
    if (result?.key === computed.key) message('인증코드를 복사했습니다.');
  } catch {
    if (result?.key !== computed.key) return;
    message('복사할 수 없습니다. 표시된 코드를 길게 눌러 직접 복사해 주세요.');
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(code);
    selection?.removeAllRanges();
    selection?.addRange(range);
  }
});
document.addEventListener('visibilitychange', tick);
window.addEventListener('pageshow', tick);
window.addEventListener('pagehide', () => { clearTimeout(timer); timer = null; });
$('calculate').disabled = false;
