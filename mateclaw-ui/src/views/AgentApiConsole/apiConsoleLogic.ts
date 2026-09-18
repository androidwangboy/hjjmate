export type ApiProtocol = 'rest' | 'openai'

export function formatEndpoint(protocol: ApiProtocol): string {
  return protocol === 'openai'
    ? '/v1/chat/completions'
    : '/api/v1/open/agent/chat'
}

export function maskKey(key: string): string {
  if (!key) return ''
  if (key.length <= 8) return `${key.slice(0, 4)}...`
  return `${key.slice(0, 4)}...${key.slice(-4)}`
}

export function buildQuickStart(protocol: ApiProtocol, key: string, _agentId?: string | number): string {
  if (protocol === 'openai') {
    return `curl -X POST https://your-mateclaw-host${formatEndpoint(protocol)} \\\n  -H "Authorization: Bearer ${key}" \\\n  -H "Content-Type: application/json" \\\n  -d '{"model":"expert","user":"external-user-1","conversation_id":"session-1","messages":[{"role":"user","content":"你好"}]}'`
  }
  return `curl -X POST https://your-mateclaw-host${formatEndpoint(protocol)} \\\n  -H "Authorization: Bearer ${key}" \\\n  -H "Content-Type: application/json" \\\n  -d '{"endUserId":"external-user-1","conversationId":"session-1","message":"你好"}'`
}
