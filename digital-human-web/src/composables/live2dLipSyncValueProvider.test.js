import { describe, expect, it } from 'vitest'
import { Live2dLipSyncValueProvider } from './live2dLipSyncValueProvider'

describe('Live2dLipSyncValueProvider', () => {
  it('returns the latest mouth openness value through the Cubism provider interface', () => {
    const provider = new Live2dLipSyncValueProvider()

    provider.setValue(0.42)

    expect(provider.update(0.016)).toBe(true)
    expect(provider.getParameter()).toBe(0.42)
  })

  it('clamps invalid values back to zero', () => {
    const provider = new Live2dLipSyncValueProvider()

    provider.setValue(Number.NaN)

    expect(provider.getParameter()).toBe(0)
  })
})
