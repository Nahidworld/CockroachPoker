// REST calls to the backend (room management + the private player view).
// In development Vite proxies /api to the backend (see vite.config.js).

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

const UNREACHABLE = 'Cannot reach the game server. Is the backend running?'

async function request(method, path, { body, token } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['X-Player-Token'] = token

  let res
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, UNREACHABLE)
  }

  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }

  if (!res.ok) {
    // the dev proxy answers 5xx with no JSON body when the backend is down
    if (res.status >= 500 && !data) throw new ApiError(res.status, UNREACHABLE)
    throw new ApiError(res.status, data?.detail || data?.message || `Request failed (${res.status})`)
  }
  return data
}

const roomPath = (code) => `/api/rooms/${encodeURIComponent(code)}`

export const createRoom = (playerName) =>
  request('POST', '/api/rooms', { body: { playerName } })

export const joinRoom = (code, playerName) =>
  request('POST', `${roomPath(code)}/join`, { body: { playerName } })

export const startGame = (code, playerId) =>
  request('POST', `${roomPath(code)}/start`, { body: { playerId } })

export const getPlayerView = (code, token) =>
  request('GET', `${roomPath(code)}/me`, { token })
