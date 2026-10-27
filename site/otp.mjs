/** CMC 9.1.00.15 challenge. Java signed-int overflow and UTC field order are intentional. */
export function normalizeKey(input) {
  const key = String(input ?? '').trim().toLowerCase();
  if (!/^[a-z0-9]{5}$/.test(key)) throw new Error('영문·숫자로 된 KEY 5자리를 입력해 주세요.');
  return key;
}
export function hash33(input) {
  let hash = 0;
  for (let i = 0; i < input.length; i++) hash = (Math.imul(hash, 33) + input.charCodeAt(i)) | 0;
  return hash;
}
export function javaAbsolute(value) { return value < 0 ? (-value | 0) : value; }
export function calculate(input, epochMillis = Date.now()) {
  const key = normalizeKey(input);
  const date = new Date(epochMillis);
  if (!Number.isFinite(date.getTime())) throw new Error('기기의 날짜와 시간을 확인해 주세요.');
  const two = value => String(value).padStart(2, '0');
  const challenge = key + [date.getUTCFullYear() - 2000, date.getUTCMonth() + 1,
    date.getUTCMinutes(), date.getUTCDate(), date.getUTCHours()].map(two).join('');
  const utcMinute = `${date.getUTCFullYear()}-${two(date.getUTCMonth() + 1)}-${two(date.getUTCDate())} ${two(date.getUTCHours())}:${two(date.getUTCMinutes())} UTC`;
  return { key, code: String(javaAbsolute(hash33(challenge))), minute: Math.floor(epochMillis / 60000), utcMinute };
}
export function secondsToNextCode(epochMillis = Date.now()) {
  const remainder = ((epochMillis % 60000) + 60000) % 60000;
  return 60 - Math.floor(remainder / 1000);
}
