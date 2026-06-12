const WAV_HEADER_PREFIX = 'UklG'
const MP3_HEADER_PREFIXES = ['SUQz', '//uQ', '//sQ', '/+MY', '/+Ng']

export function inferAudioMimeType(base64) {
  if (!base64 || typeof base64 !== 'string') {
    return 'audio/wav'
  }

  if (base64.startsWith(WAV_HEADER_PREFIX)) {
    return 'audio/wav'
  }

  if (MP3_HEADER_PREFIXES.some(prefix => base64.startsWith(prefix))) {
    return 'audio/mpeg'
  }

  // 无法识别的格式（如原始 PCM）：回退为 audio/wav，
  // 浏览器对 WAV 的兼容性更好，且后端修复后会返回正确格式。
  return 'audio/wav'
}

export function buildAudioDataUrl(base64) {
  return `data:${inferAudioMimeType(base64)};base64,${base64}`
}
