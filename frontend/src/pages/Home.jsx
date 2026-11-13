import { useState } from 'react'

export default function Home({ onCreate, onJoin }) {
  const [name, setName] = useState('')
  const [joining, setJoining] = useState(false) // shows the code input
  const [code, setCode] = useState('')
  const [err, setErr] = useState('')

  const checkName = () => {
    if (!name.trim()) { setErr('Please input your name first.'); return false }
    return true
  }

  // Create Room works immediately — one click, no second button
  const create = () => {
    if (!checkName()) return
    setErr('')
    onCreate(name.trim())
  }

  const join = () => {
    if (!checkName()) return
    if (!/^\d{4}$/.test(code.trim())) { setErr('Room code is 4 digits (e.g. 4673).'); return }
    setErr('')
    const e = onJoin(name.trim(), code.trim())
    if (e) setErr(e)
  }

  return (
    <div className="page home">
      <div className="home-card">
        {/* left: the box art, floating with a glow */}
        <div className="home-left">
          <img className="boxart" src="/boxart.jpg" alt="Cockroach Poker — Royal" />
          <div className="mini-cards">
            <span>🪳</span><span>🦂</span><span>🕷️</span><span>🦇</span>
          </div>
        </div>

        {/* right: welcome text + form */}
        <div className="home-right">
          <p className="welcome">✨ Welcome to ✨</p>
          <h1 className="title shine">Cockroach Poker</h1>
          <p className="tagline">A bluffing card game of royal proportions! 👑</p>

          <input
            className="input"
            placeholder="Input your name"
            value={name}
            maxLength={16}
            onChange={(e) => { setName(e.target.value); setErr('') }}
          />

          {!joining && (
            <div className="btn-row">
              <button className="btn gold" onClick={create}>🏠 Create Room</button>
              <button className="btn ghost" onClick={() => { setJoining(true); setErr('') }}>
                🔑 Join Room
              </button>
            </div>
          )}

          {joining && (
            <>
              <input
                className="input code-input"
                placeholder="Room code (e.g. 4673)"
                inputMode="numeric"
                maxLength={4}
                autoFocus
                value={code}
                onChange={(e) => { setCode(e.target.value.replace(/\D/g, '')); setErr('') }}
                onKeyDown={(e) => e.key === 'Enter' && join()}
              />
              <div className="btn-row">
                <button className="btn gold" onClick={join}>🚪 Join Room</button>
                <button className="btn ghost" onClick={() => { setJoining(false); setErr(''); setCode('') }}>
                  ← back
                </button>
              </div>
            </>
          )}

          {err && <p className="err">{err}</p>}

          <p className="hint">
            Create a room and share the 4-digit code — friends join from another tab,
            or fill empty seats with bots.
          </p>
        </div>
      </div>
    </div>
  )
}
