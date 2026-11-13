import { useCallback, useEffect, useRef, useState } from 'react'
import { createRoom, getPlayerView, joinRoom, startGame } from './api'
import { connectRoom } from './socket'
import { creatureById } from './game'
import Home from './pages/Home'
import Lobby from './pages/Lobby'
import Table from './pages/Table'
import GameOver from './pages/GameOver'

// { code, playerId, token } for this tab only, so two tabs can be two players
const SESSION_KEY = 'cp_session'
const LOG_SIZE = 30

function loadSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY))
  } catch {
    return null
  }
}

// Turn a public room event into a log line (null = not worth logging).
function describe(event, players) {
  const name = (id) => players.find((p) => p.id === id)?.name ?? 'Someone'
  switch (event.type) {
    case 'PLAYER_JOINED':
      return `👋 ${event.message}`
    case 'GAME_STARTED':
      return '🎲 Game on! Cards are dealt.'
    case 'CARD_PASSED': {
      const c = creatureById(event.declaredCreature)
      return `${name(event.actorPlayerId)} slides a card to ${name(event.targetPlayerId)} — claims ${c.emoji} ${c.name}`
    }
    case 'CHALLENGE_RESOLVED': {
      const actual = creatureById(event.creatureType)
      return `⚡ ${name(event.actorPlayerId)} calls it — card was ${actual.emoji} ${actual.name}. ${name(event.targetPlayerId)} takes it 😖`
    }
    case 'GAME_FINISHED':
      return `💀 ${event.message}`
    default:
      return null
  }
}

// Public summary of the last challenge, derived from a CHALLENGE_RESOLVED event.
function toResolution(event) {
  const truthful = event.declaredCreature === event.creatureType
  const correct = event.targetPlayerId !== event.actorPlayerId // a wrong caller takes the card
  return {
    challengerId: event.actorPlayerId,
    recipientId: event.targetPlayerId,
    declared: event.declaredCreature,
    actual: event.creatureType,
    truthful,
    correct,
    guessedTrue: correct ? truthful : !truthful,
  }
}

export default function App() {
  const [session, setSession] = useState(loadSession)
  const [view, setView] = useState(null)
  const [log, setLog] = useState([])
  const [resolution, setResolution] = useState(null)
  const [peekResult, setPeekResult] = useState(null)
  const [connected, setConnected] = useState(false)
  const [toast, setToast] = useState('')
  const [homeError, setHomeError] = useState('')

  const socketRef = useRef(null)
  const viewRef = useRef(null)
  const refreshSeq = useRef(0)
  const toastTimer = useRef(null)

  useEffect(() => {
    viewRef.current = view
  }, [view])

  useEffect(() => {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(SESSION_KEY)
  }, [session])

  const showToast = useCallback((message) => {
    setToast(message)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(''), 4000)
  }, [])

  const leave = useCallback((message = '') => {
    setSession(null)
    setView(null)
    setLog([])
    setResolution(null)
    setPeekResult(null)
    setHomeError(message)
  }, [])

  // Re-read this player's view of the game. Only the newest response is applied.
  const refresh = useCallback(async () => {
    if (!session) return
    const seq = ++refreshSeq.current
    try {
      const next = await getPlayerView(session.code, session.token)
      if (seq === refreshSeq.current) setView(next)
    } catch (e) {
      if (seq !== refreshSeq.current) return
      if (e.status === 403 || e.status === 404) leave('That room no longer exists (the server may have restarted).')
      else showToast(e.message)
    }
  }, [session, leave, showToast])

  // One STOMP connection per room; every public event triggers a fresh view.
  useEffect(() => {
    if (!session) return undefined
    refresh()
    const socket = connectRoom(session.code, {
      onConnect: refresh,
      onConnectionChange: setConnected,
      onEvent: (event) => {
        const line = describe(event, viewRef.current?.players ?? [])
        if (line) setLog((l) => [...l, line].slice(-LOG_SIZE))
        if (event.type === 'CHALLENGE_RESOLVED') setResolution(toResolution(event))
        if (event.type === 'GAME_STARTED') setResolution(null)
        refresh()
      },
      onPeek: (result) => {
        setPeekResult(result)
        refresh()
      },
      onError: (error) => showToast(error.message || 'Something went wrong'),
    })
    socketRef.current = socket
    return () => {
      socket.close()
      socketRef.current = null
      setConnected(false)
    }
  }, [session, refresh, showToast])

  const enterRoom = (res) => {
    setHomeError('')
    setLog([])
    setResolution(null)
    setPeekResult(null)
    setView(null)
    setSession({ code: res.roomCode, playerId: res.playerId, token: res.playerToken })
  }

  // Home handlers return an error message, or null on success.
  const create = async (name) => {
    try {
      enterRoom(await createRoom(name))
      return null
    } catch (e) {
      return e.message
    }
  }

  const join = async (name, code) => {
    try {
      enterRoom(await joinRoom(code, name))
      return null
    } catch (e) {
      return e.status === 404 ? 'Room not found — check the code.' : e.message
    }
  }

  const send = (action, body) =>
    socketRef.current?.send(action, { playerId: session.playerId, ...body })

  const actions = {
    play: (cardId, targetPlayerId, declaredCreature) =>
      send('play', { cardId, targetPlayerId, declaredCreature }),
    peek: () => send('peek', {}),
    pass: (targetPlayerId, declaredCreature) => send('pass', { targetPlayerId, declaredCreature }),
    challenge: (guess) => send('challenge', { guess }),
  }

  const start = async () => {
    try {
      await startGame(session.code, session.playerId)
      refresh()
    } catch (e) {
      showToast(e.message)
    }
  }

  let page
  if (!session) {
    page = <Home onCreate={create} onJoin={join} initialError={homeError} />
  } else if (!view) {
    page = (
      <div className="page">
        <div className="lobby-card"><h2>Connecting to room {session.code}…</h2></div>
      </div>
    )
  } else if (view.status === 'WAITING') {
    page = <Lobby view={view} onStart={start} onLeave={() => leave()} />
  } else if (view.status === 'FINISHED') {
    page = <GameOver view={view} log={log} onLeave={() => leave()} />
  } else {
    page = (
      <Table
        view={view}
        log={log}
        resolution={resolution}
        peekResult={peekResult}
        onDismissPeek={() => setPeekResult(null)}
        actions={actions}
        onLeave={() => leave()}
      />
    )
  }

  return (
    <>
      {page}
      {session && view && !connected && <div className="conn-banner">🔌 Reconnecting to the game server…</div>}
      {toast && <div className="toast" onClick={() => setToast('')}>⚠️ {toast}</div>}
    </>
  )
}
