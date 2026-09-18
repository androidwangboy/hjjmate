import { describe, expect, it } from 'vitest'
import { buildQuickStart, formatEndpoint, maskKey } from '../apiConsoleLogic'

describe('agent API console helpers', () => {
  it('formats REST and OpenAI endpoints', () => {
    expect(formatEndpoint('rest')).toContain('/api/v1/open/agent/chat')
    expect(formatEndpoint('openai')).toContain('/v1/chat/completions')
  })

  it('masks an expert API key while preserving prefix and suffix', () => {
    expect(maskKey('mak_abcdefghijklmnopqrstuvwxyz')).toBe('mak_...wxyz')
  })

  it('builds a copyable curl example with Bearer authentication', () => {
    const curl = buildQuickStart('rest', 'mak_demo', 42)
    expect(curl).toContain('Authorization: Bearer mak_demo')
    expect(curl).toContain('/api/v1/open/agent/chat')
    expect(curl).not.toContain('agentId=42')
  })
})
