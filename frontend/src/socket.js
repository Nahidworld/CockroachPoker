// STOMP-over-WebSocket connection for one room.
//  - room-wide public events:  /topic/rooms/{code}
//  - private peek results:     /user/queue/peek
//  - private command errors:   /user/queue/errors
//  - gameplay commands:        /app/rooms/{code}/{play|peek|pass|challenge}
import { Client } from '@stomp/stompjs'

export function connectRoom(code, { onConnect, onEvent, onPeek, onError, onConnectionChange }) {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const parse = (handler) => (message) => {
    try {
      handler(JSON.parse(message.body))
    } catch {
      // ignore malformed frames
    }
  }

  const client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 2000,
    onConnect: () => {
      client.subscribe(`/topic/rooms/${code}`, parse(onEvent))
      client.subscribe('/user/queue/peek', parse(onPeek))
      client.subscribe('/user/queue/errors', parse(onError))
      onConnectionChange(true)
      onConnect()
    },
    onWebSocketClose: () => onConnectionChange(false),
    onStompError: (frame) =>
      onError({ code: 'STOMP_ERROR', message: frame.headers.message || 'Connection error' }),
  })
  client.activate()

  return {
    send(action, body) {
      if (!client.connected) {
        onError({ code: 'NOT_CONNECTED', message: 'Not connected to the game server yet — try again in a moment.' })
        return
      }
      client.publish({
        destination: `/app/rooms/${code}/${action}`,
        body: JSON.stringify(body),
        headers: { 'content-type': 'application/json' },
      })
    },
    close() {
      client.deactivate()
    },
  }
}
