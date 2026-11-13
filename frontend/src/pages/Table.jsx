import { useEffect, useState } from 'react'
import {
  CREATURES, creatureById, startPile, passPile, callBluff, botAct, legalPassTargets,
} from '../game'

export default function Table({ state, myId, me, update, exit }) {
  // local (per-tab) UI state — never shared with other players
  const [selCard, setSelCard] = useState(null)
  const [selCreature, setSelCreature] = useState(null)
  const [selTarget, setSelTarget] = useState(null)
  const [peeked, setPeeked] = useState(false)
  const [showPeek, setShowPeek] = useState(false)

  const isMyTurn = state.phase === 'start-pile' && state.turn === myId
  const iRespond = state.phase === 'respond' && state.responder === myId

  // drive bot players
  useEffect(() => {
    if (state.phase !== 'start-pile' && state.phase !== 'respond') return
    const actorId = state.phase === 'start-pile' ? state.turn : state.responder
    const actor = state.players.find((p) => p.id === actorId)
    if (actor?.isBot) {
      const t = setTimeout(() => update((s) => botAct(s)), 1200)
      return () => clearTimeout(t)
    }
  }, [state])

  // reset selections when the turn changes
  useEffect(() => {
    setSelCard(null); setSelCreature(null); setSelTarget(null); setPeeked(false)
  }, [state.turn, state.responder, state.phase])

  const claimer = state.lastClaim && state.players.find((p) => p.id === state.lastClaim.by)
  const targetable = () => {
    if (isMyTurn) return state.players.filter((p) => p.id !== myId && !p.out)
    if (iRespond && (selCreature !== null || peeked)) return legalPassTargets(state, myId)
    return []
  }
  const canTarget = targetable()

  const confirmStartPile = () => {
    if (selCard && selCreature && selTarget)
      update((s) => startPile(s, myId, selCard, selCreature, selTarget))
  }
  const confirmPass = () => {
    if (selCreature && selTarget)
      update((s) => passPile(s, myId, selTarget, selCreature))
  }

  // ---- seat positions around the oval table (me at the bottom) ----
  const n = state.players.length
  const myIndex = state.players.findIndex((p) => p.id === myId)
  const seats = state.players.map((p, i) => {
    const deg = 90 + (360 * ((i - myIndex + n) % n)) / n
    const rad = (deg * Math.PI) / 180
    return { p, left: 50 + 41 * Math.cos(rad), top: 46 + 36 * Math.sin(rad) }
  })

  return (
    <div className="page table-page">
      <header className="table-header">
        <span className="mini-code">Room {state.code}</span>
        <span className="turn-banner">
          {state.phase === 'respond'
            ? `⏳ ${state.players.find((p) => p.id === state.responder)?.name} must Peek, Pass or Call Bluff`
            : `🎯 ${state.players.find((p) => p.id === state.turn)?.name}'s turn`}
        </span>
        <button className="link" onClick={exit}>leave</button>
      </header>

      <div className="table-area">
        <div className="felt">
          {/* center: room code + travelling card */}
          <div className="center-info">
            <div className="code-badge small">{state.code}</div>

            {state.pileCard && claimer && (
              <div className="pile-zone">
                <div className="claim-text">
                  <strong>{claimer.name}</strong> claims it is{' '}
                  <strong>{creatureById(state.lastClaim.creature).emoji} {creatureById(state.lastClaim.creature).name}</strong>
                </div>
                <div className={`card back dealt ${state.responder === myId ? 'tome' : ''}`} />
                <div className="claim-text">→ waiting for <strong>{state.players.find((p) => p.id === state.responder)?.name}</strong></div>
              </div>
            )}

            {state.lastResolution && !state.pileCard && (
              <div className={`resolution ${state.lastResolution.matched ? 'true' : 'bluff'}`}>
                <div>
                  Card was <strong>{creatureById(state.lastResolution.card.creature).emoji} {creatureById(state.lastResolution.card.creature).name}</strong>
                  {' '}· claimed <strong>{creatureById(state.lastResolution.claim.creature).emoji} {creatureById(state.lastResolution.claim.creature).name}</strong>
                </div>
                <div>
                  {state.lastResolution.matched
                    ? `✔ Claim was TRUE — caller ${state.lastResolution.callerName} takes it 😖`
                    : `❌ It WAS a bluff — ${state.lastResolution.claim.byName} takes it 😖`}
                </div>
              </div>
            )}

            {!state.pileCard && !state.lastResolution && (
              <div className="claim-text dim">no card on the table</div>
            )}
          </div>

          {/* seats */}
          {seats.map(({ p, left, top }) => {
            const isTargetable = canTarget.some((t) => t.id === p.id)
            const active = p.id === state.turn || p.id === state.responder
            const cls = [
              'seat',
              p.out && 'out',
              active && 'active',
              isTargetable && 'targetable',
              selTarget === p.id && 'selected',
            ].filter(Boolean).join(' ')
            return (
              <div
                key={p.id}
                className={cls}
                style={{ left: `${left}%`, top: `${top}%` }}
                onClick={() => isTargetable && setSelTarget(p.id)}
              >
                <div className="seat-name">{p.name}{p.isBot && ' 🤖'}{p.id === myId && ' (you)'}</div>
                <div className="seat-body">
                  <span className="card-mini">🂠 {p.hand.length}</span>
                  <span className="faceup">{p.faceUp.map((c) => creatureById(c.creature).emoji).join(' ')}</span>
                </div>
                {p.out && <div className="out-badge">💀 OUT</div>}
              </div>
            )
          })}
        </div>

        {/* log */}
        <div className="log">
          {state.log.slice(-4).map((l, i) => <div key={i}>{l}</div>)}
        </div>
      </div>

      {/* ---------- action bar ---------- */}
      <div className="action-bar">
        {isMyTurn && (
          <>
            <div className="hand">
              {me.hand.map((c) => (
                <div
                  key={c.id}
                  className={`card ${selCard === c.id ? 'selected' : ''}`}
                  onClick={() => setSelCard(c.id)}
                >
                  <span className="ce">{creatureById(c.creature).emoji}</span>
                  <span className="cn">{creatureById(c.creature).name}</span>
                </div>
              ))}
            </div>
            <div className="panel">
              <div className="steps">
                1️⃣ pick a card &nbsp; 2️⃣ declare a creature (lie if you dare!) &nbsp; 3️⃣ pick a player
              </div>
              <div className="creature-grid">
                {CREATURES.map((c) => (
                  <button
                    key={c.id}
                    className={`chip ${selCreature === c.id ? 'selected' : ''}`}
                    onClick={() => setSelCreature(c.id)}
                  >
                    {c.emoji} {c.name}
                  </button>
                ))}
              </div>
              <button
                className="btn gold big"
                disabled={!selCard || !selCreature || !selTarget}
                onClick={confirmStartPile}
              >
                🂠 Pass card to {selTarget ? state.players.find((p) => p.id === selTarget)?.name : '…'}
              </button>
            </div>
          </>
        )}

        {iRespond && !peeked && (
          <div className="panel">
            <div className="steps">A face-down card was slid to you. Peek… or trust it?</div>
            <div className="btn-row">
              <button className="btn ghost" onClick={() => setShowPeek(true)}>👁 Peek</button>
              <button className="btn danger" onClick={() => update((s) => callBluff(s, myId))}>
                ⚡ Call Bluff
              </button>
              <button className="btn gold" onClick={() => setPeeked(true)}>➜ Pass it on</button>
            </div>
            <p className="hint">You can’t pass it back to the player who gave it to you.</p>
          </div>
        )}

        {(iRespond && peeked) && (
          <div className="panel">
            <div className="steps">Pass it to another player and declare a creature (truth… or bluff).</div>
            <div className="creature-grid">
              {CREATURES.map((c) => (
                <button
                  key={c.id}
                  className={`chip ${selCreature === c.id ? 'selected' : ''}`}
                  onClick={() => setSelCreature(c.id)}
                >
                  {c.emoji} {c.name}
                </button>
              ))}
            </div>
            <button
              className="btn gold big"
              disabled={!selCreature || !selTarget}
              onClick={confirmPass}
            >
              ➜ Pass to {selTarget ? state.players.find((p) => p.id === selTarget)?.name : '…'}
            </button>
          </div>
        )}

        {!isMyTurn && !iRespond && (
          <div className="panel waiting">
            ⏳ Waiting for {state.phase === 'respond'
              ? state.players.find((p) => p.id === state.responder)?.name
              : state.players.find((p) => p.id === state.turn)?.name}…
            <div className="hand mine-small">
              {me.hand.map((c) => (
                <div key={c.id} className="card small">
                  <span className="ce">{creatureById(c.creature).emoji}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* my penalty cards */}
      {me.faceUp.length > 0 && (
        <div className="penalty">
          Your collected cards: {me.faceUp.map((c) => creatureById(c.creature).emoji).join(' ')}
        </div>
      )}

      {/* peek modal (only I see it) */}
      {showPeek && (
        <div className="modal" onClick={() => setShowPeek(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <h3>👁 You peek at the card…</h3>
            <div className="card big">
              <span className="ce">{creatureById(state.pileCard.creature).emoji}</span>
              <span className="cn">{creatureById(state.pileCard.creature).name}</span>
            </div>
            <p className="hint">Only you can see this. Now you must pass it on — say any creature you like.</p>
            <button className="btn gold" onClick={() => { setPeeked(true); setShowPeek(false) }}>
              Got it — Pass it on
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
