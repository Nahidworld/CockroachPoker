import { useEffect, useState } from 'react'
import { CREATURES, creatureById, eligiblePassTargets, playerName } from '../game'

function CreaturePicker({ selected, onSelect }) {
  return (
    <div className="creature-grid">
      {CREATURES.map((c) => (
        <button
          key={c.id}
          className={`chip ${selected === c.id ? 'selected' : ''}`}
          onClick={() => onSelect(c.id)}
        >
          {c.emoji} {c.name}
        </button>
      ))}
    </div>
  )
}

const label = (id) => {
  const c = creatureById(id)
  return `${c.emoji} ${c.name}`
}

export default function Table({ view, log, resolution, peekResult, onDismissPeek, actions, onLeave }) {
  const myId = view.playerId
  const pending = view.pendingCard
  const me = view.players.find((p) => p.id === myId)
  const name = (id) => playerName(view, id)

  const isMyTurn = !pending && view.currentPlayerId === myId
  const iRespond = !!pending && pending.currentReceiverId === myId
  const hasPeeked = iRespond && view.peekedCreature != null
  const passTargets = iRespond ? eligiblePassTargets(view) : []

  // local (per-tab) selections — never shared with other players
  const [selCard, setSelCard] = useState(null)
  const [selCreature, setSelCreature] = useState(null)
  const [selTarget, setSelTarget] = useState(null)

  useEffect(() => {
    setSelCard(null); setSelCreature(null); setSelTarget(null)
  }, [view.currentPlayerId, pending?.currentReceiverId, hasPeeked])

  const targetable = isMyTurn
    ? view.players.filter((p) => p.id !== myId)
    : hasPeeked ? passTargets : []

  // ---- seat positions around the oval table (me at the bottom) ----
  const n = view.players.length
  const myIndex = view.players.findIndex((p) => p.id === myId)
  const seats = view.players.map((p, i) => {
    const deg = 90 + (360 * ((i - myIndex + n) % n)) / n
    const rad = (deg * Math.PI) / 180
    return { p, left: 50 + 41 * Math.cos(rad), top: 46 + 36 * Math.sin(rad) }
  })

  return (
    <div className="page table-page">
      <header className="table-header">
        <span className="mini-code">Room {view.roomCode}</span>
        <span className="turn-banner">
          {pending
            ? `⏳ ${name(pending.currentReceiverId)} must call it — or peek & pass`
            : `🎯 ${name(view.currentPlayerId)}'s turn`}
        </span>
        <button className="link" onClick={onLeave}>leave</button>
      </header>

      <div className="table-area">
        <div className="felt">
          {/* center: room code + travelling card */}
          <div className="center-info">
            <div className="code-badge small">{view.roomCode}</div>

            {pending && (
              <div className="pile-zone">
                <div className="claim-text">
                  <strong>{name(pending.latestClaimantId)}</strong> claims it is{' '}
                  <strong>{label(pending.declaredCreature)}</strong>
                </div>
                <div className={`card back dealt ${iRespond ? 'tome' : ''}`} />
                <div className="claim-text">→ waiting for <strong>{name(pending.currentReceiverId)}</strong></div>
              </div>
            )}

            {!pending && resolution && (
              <div className={`resolution ${resolution.truthful ? 'true' : 'bluff'}`}>
                <div>
                  Card was <strong>{label(resolution.actual)}</strong> · claimed <strong>{label(resolution.declared)}</strong>
                </div>
                <div>
                  {name(resolution.challengerId)} said {resolution.guessedTrue ? 'TRUE' : 'BLUFF'} —{' '}
                  {resolution.correct ? 'right!' : 'wrong!'} {name(resolution.recipientId)} takes it 😖
                </div>
              </div>
            )}

            {!pending && !resolution && <div className="claim-text dim">no card on the table</div>}
          </div>

          {/* seats */}
          {seats.map(({ p, left, top }) => {
            const isTargetable = targetable.some((t) => t.id === p.id)
            const active = pending ? p.id === pending.currentReceiverId : p.id === view.currentPlayerId
            const cls = [
              'seat',
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
                <div className="seat-name">{p.name}{p.id === myId && ' (you)'}</div>
                <div className="seat-body">
                  <span className="card-mini">🂠 {p.handCount}</span>
                  <span className="faceup">{p.collectedCards.map((c) => creatureById(c).emoji).join(' ')}</span>
                </div>
              </div>
            )
          })}
        </div>

        {/* log */}
        <div className="log">
          {log.slice(-4).map((l, i) => <div key={i}>{l}</div>)}
        </div>
      </div>

      {/* ---------- action bar ---------- */}
      <div className="action-bar">
        {isMyTurn && (
          <>
            <div className="hand">
              {view.hand.map((c) => (
                <div
                  key={c.id}
                  className={`card ${selCard === c.id ? 'selected' : ''}`}
                  onClick={() => setSelCard(c.id)}
                >
                  <span className="ce">{creatureById(c.creatureType).emoji}</span>
                  <span className="cn">{creatureById(c.creatureType).name}</span>
                </div>
              ))}
            </div>
            <div className="panel">
              <div className="steps">
                1️⃣ pick a card &nbsp; 2️⃣ declare a creature (lie if you dare!) &nbsp; 3️⃣ pick a player
              </div>
              <CreaturePicker selected={selCreature} onSelect={setSelCreature} />
              <button
                className="btn gold big"
                disabled={!selCard || !selCreature || !selTarget}
                onClick={() => actions.play(selCard, selTarget, selCreature)}
              >
                🂠 Slide card to {selTarget ? name(selTarget) : '…'}
              </button>
            </div>
          </>
        )}

        {iRespond && !hasPeeked && (
          <div className="panel">
            <div className="steps">
              {name(pending.latestClaimantId)} says it is <strong>{label(pending.declaredCreature)}</strong>. Is that true?
            </div>
            <div className="btn-row">
              <button className="btn gold" onClick={() => actions.challenge(true)}>✔ It's TRUE</button>
              <button className="btn danger" onClick={() => actions.challenge(false)}>❌ It's a BLUFF</button>
              <button className="btn ghost" disabled={passTargets.length === 0} onClick={actions.peek}>
                👁 Peek &amp; pass
              </button>
            </div>
            <p className="hint">
              {passTargets.length === 0
                ? 'Everyone else has seen this card — you must call it.'
                : 'Guess right and the claimer takes the card; guess wrong and you take it. If you peek, you must pass it on.'}
            </p>
          </div>
        )}

        {hasPeeked && (
          <div className="panel">
            <div className="steps">
              You saw <strong>{label(view.peekedCreature)}</strong>. Pick a player who hasn't seen it and make your claim (truth… or bluff).
            </div>
            <CreaturePicker selected={selCreature} onSelect={setSelCreature} />
            <button
              className="btn gold big"
              disabled={!selCreature || !selTarget}
              onClick={() => actions.pass(selTarget, selCreature)}
            >
              ➜ Pass to {selTarget ? name(selTarget) : '…'}
            </button>
          </div>
        )}

        {!isMyTurn && !iRespond && (
          <div className="panel waiting">
            ⏳ Waiting for {pending ? name(pending.currentReceiverId) : name(view.currentPlayerId)}…
            <div className="hand mine-small">
              {view.hand.map((c) => (
                <div key={c.id} className="card small">
                  <span className="ce">{creatureById(c.creatureType).emoji}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* my collected (face-up) cards */}
      {me && me.collectedCards.length > 0 && (
        <div className="penalty">
          Your collected cards: {me.collectedCards.map((c) => creatureById(c).emoji).join(' ')}
        </div>
      )}

      {/* peek result (only I see it) */}
      {peekResult && hasPeeked && (
        <div className="modal" onClick={onDismissPeek}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <h3>👁 You peek at the card…</h3>
            <div className="card big">
              <span className="ce">{creatureById(peekResult.actualCreature).emoji}</span>
              <span className="cn">{creatureById(peekResult.actualCreature).name}</span>
            </div>
            <p className="hint">Only you can see this. Now pass it on — say any creature you like.</p>
            <button className="btn gold" onClick={onDismissPeek}>Got it — pass it on</button>
          </div>
        </div>
      )}
    </div>
  )
}
