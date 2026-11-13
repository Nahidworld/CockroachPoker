import { AVATARS, LOSING_COUNT, countByCreature, creatureById, playerName } from '../game'

export default function GameOver({ view, log, onLeave }) {
  const loser = view.players.find((p) => p.id === view.loserPlayerId)
  const counts = countByCreature(loser?.collectedCards ?? [])
  const fourOfAKind = Object.keys(counts).find((c) => counts[c] >= LOSING_COUNT)
  const reason = fourOfAKind
    ? `collected ${LOSING_COUNT} × ${creatureById(fourOfAKind).emoji} ${creatureById(fourOfAKind).name}`
    : 'had no cards left to start a round'
  const iLost = view.loserPlayerId === view.playerId

  return (
    <div className="page winner-page">
      <div className="winner-card">
        <div className="crown">{iLost ? '🪳' : '👑'}</div>
        <h1>{playerName(view, view.loserPlayerId)} loses!</h1>
        <p className="sub">{iLost ? 'You' : playerName(view, view.loserPlayerId)} {reason}. Everyone else wins 🎉</p>

        <div className="standings">
          {view.players.map((p, i) => (
            <div key={p.id} className={`standing ${p.id === view.loserPlayerId ? 'lost' : 'won'}`}>
              <span className="avatar">{AVATARS[i]}</span>
              <span className="pname">{p.name}{p.id === view.playerId && ' (you)'}</span>
              <span className="faceup">{p.collectedCards.map((c) => creatureById(c).emoji).join(' ')}</span>
              <span className="tag">{p.id === view.loserPlayerId ? '💀 loser' : '🏆 safe'}</span>
            </div>
          ))}
        </div>

        <div className="btn-row">
          <button className="btn gold big" onClick={onLeave}>🏠 Back to Home</button>
        </div>

        <div className="log">
          {log.slice(-5).map((l, i) => <div key={i}>{l}</div>)}
        </div>
      </div>
    </div>
  )
}
