type IdLike = string | number

interface RouteHydrationAgent {
  id: IdLike
}

interface RouteHydrationConversation {
  conversationId: string
}

export function resolveRouteHydrationQuery(options: {
  routeAgentId?: string
  routeConversationId?: string
  agents: RouteHydrationAgent[]
  conversations: RouteHydrationConversation[]
}): { agentId: string; conversationId: string } {
  let agentId = options.routeAgentId || ''
  const conversationId = options.routeConversationId || ''

  if (agentId && options.agents.length > 0 && !options.agents.some(a => String(a.id) === agentId)) {
    agentId = ''
  }

  return { agentId, conversationId }
}
