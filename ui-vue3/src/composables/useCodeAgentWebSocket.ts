import { ref, onUnmounted } from 'vue'

export interface CodeAgentFile {
  path: string
  content: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  type: 'text' | 'agent-execution'
  content: string
  timestamp: number
  planRecord?: any       // 用于恢复历史会话时每条消息独立的 planRecord
  completed?: boolean    // 用于恢复历史会话时每条消息独立的完成状态
}

export function useCodeAgentWebSocket() {
  const connected = ref(false)
  const generating = ref(false)
  const status = ref('')
  const error = ref('')
  const messages = ref<ChatMessage[]>([])
  const streamingContent = ref('')
  const files = ref<CodeAgentFile[]>([])

  // Plan execution state
  const planId = ref('')
  const planRecord = ref<any>(null)
  const planCompleted = ref(false)
  const conversationId = ref('')

  let ws: WebSocket | null = null
  let currentAssistantContent = ''

  // --- Connection ---
  function connect() {
    if (ws && ws.readyState === WebSocket.OPEN) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}/ws/code-agent`

    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      error.value = ''
      console.log('[CodeAgent WS] Connected')
    }

    ws.onmessage = (event) => {
      handleMessage(event.data)
    }

    ws.onclose = () => {
      connected.value = false
      generating.value = false
      console.log('[CodeAgent WS] Disconnected')
    }

    ws.onerror = (e) => {
      error.value = 'WebSocket connection error'
      console.error('[CodeAgent WS] Error:', e)
    }
  }

  function disconnect() {
    if (ws) {
      ws.close()
      ws = null
    }
  }

  // --- Send ---
  function send(content: string) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      error.value = 'Not connected'
      return
    }

    // Add user message
    messages.value.push({
      role: 'user',
      type: 'text',
      content,
      timestamp: Date.now(),
    })

    // Reset streaming state
    currentAssistantContent = ''
    streamingContent.value = ''
    generating.value = true
    error.value = ''

    ws.send(JSON.stringify({ type: 'chat', content }))
  }

  // --- Message handler ---
  function handleMessage(raw: string) {
    try {
      const msg = JSON.parse(raw)
      console.log('[CodeAgent WS] Received:', msg.type, msg.type === 'file' ? msg.file?.path : (msg.content?.substring?.(0, 80) || ''))

      switch (msg.type) {
        case 'token':
          currentAssistantContent += msg.content || ''
          streamingContent.value = currentAssistantContent
          break

        case 'file':
          if (msg.file) {
            console.log('[CodeAgent WS] File received:', msg.file.path, `(${msg.file.content?.length || 0} bytes)`)
            const existing = files.value.find(f => f.path === msg.file.path)
            if (existing) {
              existing.content = msg.file.content
              console.log('[CodeAgent WS] Updated existing file:', msg.file.path)
            } else {
              files.value.push({ path: msg.file.path, content: msg.file.content })
              console.log('[CodeAgent WS] Added new file:', msg.file.path, `(total: ${files.value.length})`)
            }
          } else {
            console.warn('[CodeAgent WS] File message received but msg.file is missing:', msg)
          }
          break

        case 'status':
          status.value = msg.content || ''
          console.log('[CodeAgent WS] Status:', status.value)
          break

        case 'error':
          error.value = msg.content || 'Unknown error'
          generating.value = false
          console.error('[CodeAgent WS] Error:', error.value)
          break

        case 'plan_started':
          planId.value = msg.planId || ''
          planRecord.value = null
          planCompleted.value = false
          generating.value = true
          if (msg.conversationId) {
            conversationId.value = msg.conversationId
          }
          console.log('[CodeAgent WS] Plan started:', planId.value, 'conversationId:', conversationId.value)
          // Add agent-execution card message
          messages.value.push({
            role: 'assistant',
            type: 'agent-execution',
            content: '',
            timestamp: Date.now(),
          })
          break

        case 'plan_progress':
          planRecord.value = msg.planRecord || null
          break

        case 'plan_completed':
          planRecord.value = msg.planRecord || null
          planCompleted.value = true
          console.log('[CodeAgent WS] Plan completed. Files so far:', files.value.length, files.value.map(f => f.path))
          break

        case 'done':
          console.log('[CodeAgent WS] Done. Total files:', files.value.length, files.value.map(f => f.path))
          // Finalize assistant message
          if (currentAssistantContent) {
            messages.value.push({
              role: 'assistant',
              type: 'text',
              content: currentAssistantContent,
              timestamp: Date.now(),
            })
          }
          currentAssistantContent = ''
          streamingContent.value = ''
          generating.value = false
          break

        default:
          console.warn('[CodeAgent WS] Unknown message type:', msg.type, msg)
      }
    } catch (e) {
      console.error('[CodeAgent WS] Failed to parse message:', e, 'raw:', raw?.substring?.(0, 200))
    }
  }

  // --- Cleanup ---
  function reset() {
    messages.value = []
    files.value = []
    streamingContent.value = ''
    currentAssistantContent = ''
    generating.value = false
    error.value = ''
    status.value = ''
    planId.value = ''
    planRecord.value = null
    planCompleted.value = false
    conversationId.value = ''
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    generating,
    status,
    error,
    messages,
    streamingContent,
    files,
    planId,
    planRecord,
    planCompleted,
    conversationId,
    connect,
    disconnect,
    send,
    reset,
  }
}
