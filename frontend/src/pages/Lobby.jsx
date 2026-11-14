import { useEffect, useRef, useState } from 'react'
import { AVATARS, MAX_PLAYERS, MIN_PLAYERS } from '../game'

export default function Lobby({ view, onStart, onLeave }) {
  const isHost = view.hostPlayerId === view.playerId
  const playerCount = view.players.length
  const [copied, setCopied] = useState(false)
  const [starting, setStarting] = useState(false)
  const autoStarted = useRef(false)

  const copyCode = () => {
    navigator.clipboard?.writeText(view.roomCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const start = async () => {
    if (starting) return
    setStarting(true)
    await onStart()
    setStarting(false)
  }

  // a full table starts automatically (the host's tab sends the request)
  useEffect(() => {
    if (isHost && playerCount >= MAX_PLAYERS && !autoStarted.current) {
      autoStarted.current = true
      const t = setTimeout(start, 800)
      return () => clearTimeout(t)
    }
    return undefined
  }, [isHost, playerCount])

  return (
    <div className="page lobby">
      <div className="lobby-card">
        <button className="link top-left" onClick={onLeave}>← leave</button>
        <h2>Waiting Room</h2>
        <p className="sub">Share this code to invite players</p>

        <div className="code-row">
          <div className="code-badge">{view.roomCode}</div>
          <button className="copy-btn" onClick={copyCode} title="Copy code">
            {copied ? '✅' : '📋'}
          </button>
        </div>
        <p className="hint">
          {copied ? 'Copied to clipboard!' : 'click 📋 to copy the code · game auto-starts when 6 players join'}
        </p>

        <div className="players-grid">
          {view.players.map((p, i) => (
            <div key={p.id} className="player-chip">
              <span className="avatar">{AVATARS[i]}</span>
              <span className="pname">{p.name}{p.id === view.playerId && ' (you)'}</span>
              {p.id === view.hostPlayerId && <span className="tag host">HOST</span>}
            </div>
          ))}
          {Array.from({ length: MAX_PLAYERS - playerCount }).map((_, i) => (
            <div key={`empty${i}`} className="player-chip empty">
              <span className="avatar">❔</span>
              <span className="pname">waiting…</span>
            </div>
          ))}
        </div>

        {isHost ? (
          <div className="btn-row">
            <button
              className="btn gold big"
              disabled={playerCount < MIN_PLAYERS || starting}
              onClick={start}
            >
              ▶ Start Game {playerCount < MIN_PLAYERS && '(need 2+)'}
            </button>
          </div>
        ) : (
          <p className="hint">Waiting for the host to start the game…</p>
        )}
      </div>
    </div>
  )
}
