import { describe, expect, it } from 'vitest'
import { buildAudioDataUrl, inferAudioMimeType } from './audioFormat'

describe('audioFormat', () => {
  it('detects mp3 payloads from an ID3 header prefix', () => {
    expect(inferAudioMimeType('SUQzAwAAAAAA')).toBe('audio/mpeg')
  })

  it('keeps wav payloads as wav when the RIFF header is present', () => {
    expect(inferAudioMimeType('UklGRlIAAABXQVZF')).toBe('audio/wav')
  })

  it('builds a data url with the inferred mime type', () => {
    expect(buildAudioDataUrl('SUQzAwAAAAAA')).toBe('data:audio/mpeg;base64,SUQzAwAAAAAA')
  })

  it('falls back to audio/wav for unknown or missing prefixes', () => {
    expect(inferAudioMimeType('')).toBe('audio/wav')
    expect(inferAudioMimeType(null)).toBe('audio/wav')
    expect(inferAudioMimeType('AAAA')).toBe('audio/wav')
  })
})
