import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'

let stompClient = null
const subscribers = new Set()

export function connectWebSocket(onEvent) {
  if (stompClient && stompClient.connected) {
    subscribers.add(onEvent)
    return () => subscribers.delete(onEvent)
  }

  stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: 3000,
    onConnect: () => {
      console.log('[WS] Connected to order processing stream')
      stompClient.subscribe('/topic/events', (message) => {
        try {
          const event = JSON.parse(message.body)
          subscribers.forEach(fn => fn(event))
        } catch (e) {
          console.error('[WS] Failed to parse event:', e)
        }
      })
    },
    onDisconnect: () => console.log('[WS] Disconnected'),
    onStompError: (frame) => console.error('[WS] STOMP error:', frame),
  })

  stompClient.activate()
  subscribers.add(onEvent)

  return () => {
    subscribers.delete(onEvent)
    if (subscribers.size === 0 && stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
  }
}

export function isConnected() {
  return stompClient?.connected || false
}
