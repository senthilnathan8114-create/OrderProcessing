import { useEffect, useRef, useState, useCallback } from 'react'
import { connectWebSocket, isConnected } from '../services/websocket'

export function useOrderUpdates({ onOrderEvent, onInventoryEvent } = {}) {
  const [connected, setConnected] = useState(false)
  const [lastEvent, setLastEvent] = useState(null)
  const disconnectRef = useRef(null)

  const handleEvent = useCallback((event) => {
    setLastEvent(event)

    if (event.eventType === 'INVENTORY_UPDATED' && onInventoryEvent) {
      onInventoryEvent(event.payload)
    } else if (onOrderEvent) {
      onOrderEvent(event)
    }
  }, [onOrderEvent, onInventoryEvent])

  useEffect(() => {
    const disconnect = connectWebSocket(handleEvent)
    disconnectRef.current = disconnect

    const checkInterval = setInterval(() => {
      setConnected(isConnected())
    }, 1000)

    return () => {
      disconnect()
      clearInterval(checkInterval)
    }
  }, [handleEvent])

  return { connected, lastEvent }
}
