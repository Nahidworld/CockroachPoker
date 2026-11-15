// STOMP-over-WebSocket connection for one room.
//  - room-wide public events:  /topic/rooms/{code}
//  - private peek results:     /user/queue/peek
//  - private command errors:   /user/queue/errors
//  - CONNECT carries X-Room-Code and X-Player-Token; if the player's connection drops for
//    more than 60 s, the server removes them (a forfeit during a game)
//  - gameplay commands:        /app/rooms/{code}/{play|peek|pass|challenge}
//    (each command carries the player's secret token in the X-Player-Token header)
import { Client } from '@stomp/stompjs'
import { BACKEND_URL } from './api'

// Same host as the page in development (Vite proxies /ws); the backend's own host in production.
function socketUrl() {
  if (BACKEND_URL) return `${BACKEND_URL.replace(/^http/, 'ws')}/ws`
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

export function connectRoom(code, playerToken, { onConnect, onEvent, onPeek, onError, onConnectionChange }) {
  const parse = (handler) => (message) => {
    try {
      handler(JSON.parse(message.body))
    } catch {
      // ignore malformed frames
    }
  }

  const client = new Client({
    brokerURL: socketUrl(),
    // links this connection to the player, so the server notices when they drop out
    connectHeaders: { 'X-Room-Code': code, 'X-Player-Token': playerToken },
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
        headers: { 'content-type': 'application/json', 'X-Player-Token': playerToken },
      })
    },
    close() {
      client.deactivate()
    },
  }
}
