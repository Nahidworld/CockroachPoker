import { useEffect, useState } from 'react'
import { AVATARS, addBot, startGame } from '../game'

export default function Lobby({ state, me, update, exit }) {
  const isHost = state.players[0].id === me?.id
  const full = state.players.length >= 6
  const [copied, setCopied] = useState(false)

  const copyCode = () => {
    navigator.clipboard?.writeText(state.code)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  // everyone joined -> game starts automatically
  useEffect(() => {
    if (state.phase === 'lobby' && state.players.length >= 6 && isHost) {
      const t = setTimeout(() => update((s) => startGame(s)), 800)
      return () => clearTimeout(t)
    }
  }, [state.players.length, state.phase, isHost])

  return (
    <div className="page lobby">
      <div className="lobby-card">
        <button className="link top-left" onClick={exit}>← leave</button>
        <h2>Waiting Room</h2>
        <p className="sub">Share this code to invite players</p>

        <div className="code-row">
          <div className="code-badge">{state.code}</div>
          <button className="copy-btn" onClick={copyCode} title="Copy code">
            {copied ? '✅' : '📋'}
          </button>
        </div>
        <p className="hint">
          {copied ? 'Copied to clipboard!' : 'click 📋 to copy the code · game auto-starts when 6 players join'}
        </p>

        <div className="players-grid">
          {state.players.map((p, i) => (
            <div key={p.id} className="player-chip">
              <span className="avatar">{AVATARS[i]}</span>
              <span className="pname">{p.name}</span>
              {p.isBot && <span className="tag">BOT</span>}
              {i === 0 && <span className="tag host">HOST</span>}
            </div>
          ))}
          {Array.from({ length: 6 - state.players.length }).map((_, i) => (
            <div key={`empty${i}`} className="player-chip empty">
              <span className="avatar">❔</span>
              <span className="pname">waiting…</span>
            </div>
          ))}
        </div>

        {isHost ? (
          <div className="btn-row">
            <button className="btn ghost" disabled={full} onClick={() => update((s) => addBot(s))}>
              🤖 Add Bot {full && '(full)'}
            </button>
            <button
              className="btn gold big"
              disabled={state.players.length < 2}
              onClick={() => update((s) => startGame(s))}
            >
              ▶ Start Game {state.players.length < 2 && '(need 2+)'}
            </button>
          </div>
        ) : (
          <p className="hint">Waiting for the host to start the game…</p>
        )}
      </div>
    </div>
  )
}
