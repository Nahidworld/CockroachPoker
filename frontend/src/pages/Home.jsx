import { useState } from 'react'

const CODE_LENGTH = 6

export default function Home({ onCreate, onJoin, initialError = '' }) {
  const [name, setName] = useState('')
  const [joining, setJoining] = useState(false) // shows the code input
  const [code, setCode] = useState('')
  const [err, setErr] = useState(initialError)
  const [busy, setBusy] = useState(false)

  const checkName = () => {
    if (!name.trim()) { setErr('Please input your name first.'); return false }
    return true
  }

  const run = async (action) => {
    setBusy(true)
    setErr('')
    const e = await action()
    setBusy(false)
    if (e) setErr(e)
  }

  const create = () => {
    if (!checkName() || busy) return
    run(() => onCreate(name.trim()))
  }

  const join = () => {
    if (!checkName() || busy) return
    if (code.length !== CODE_LENGTH) {
      setErr(`Room code is ${CODE_LENGTH} letters/numbers (e.g. 7KQ2ZD).`)
      return
    }
    run(() => onJoin(name.trim(), code))
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
            onKeyDown={(e) => e.key === 'Enter' && !joining && create()}
          />

          {!joining && (
            <div className="btn-row">
              <button className="btn gold" disabled={busy} onClick={create}>🏠 Create Room</button>
              <button className="btn ghost" disabled={busy} onClick={() => { setJoining(true); setErr('') }}>
                🔑 Join Room
              </button>
            </div>
          )}

          {joining && (
            <>
              <input
                className="input code-input"
                placeholder="CODE"
                autoCapitalize="characters"
                maxLength={CODE_LENGTH}
                autoFocus
                value={code}
                onChange={(e) => {
                  setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))
                  setErr('')
                }}
                onKeyDown={(e) => e.key === 'Enter' && join()}
              />
              <div className="btn-row">
                <button className="btn gold" disabled={busy} onClick={join}>🚪 Join Room</button>
                <button className="btn ghost" disabled={busy} onClick={() => { setJoining(false); setErr(''); setCode('') }}>
                  ← back
                </button>
              </div>
            </>
          )}

          {err && <p className="err">{err}</p>}

          <p className="hint">
            Create a room and share the 6-character code — friends join from their own
            browser (or another tab of yours). 2–6 players.
          </p>
        </div>
      </div>
    </div>
  )
}
