// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { resolveRouteHydrationQuery } from '@/utils/chatRouteHydration'

const agents = [{ id: 'agent-visible' }]
const conversations = [{ conversationId: 'conv-listed' }]

describe('resolveRouteHydrationQuery', () => {
  it('keeps a deep-linked child conversation even when it is not in the sidebar list', () => {
    const result = resolveRouteHydrationQuery({
      routeAgentId: 'agent-deleted-or-hidden',
      routeConversationId: 'team-task-finished',
      agents,
      conversations,
    })

    expect(result).toEqual({
      agentId: '',
      conversationId: 'team-task-finished',
    })
  })

  it('keeps valid route agent and conversation ids unchanged', () => {
    const result = resolveRouteHydrationQuery({
      routeAgentId: 'agent-visible',
      routeConversationId: 'conv-listed',
      agents,
      conversations,
    })

    expect(result).toEqual({
      agentId: 'agent-visible',
      conversationId: 'conv-listed',
    })
  })
})
