import { AVATARS, startGame, creatureById } from '../game'

export default function Winner({ state, me, update, exit }) {
  const winner = state.players.find((p) => p.id === state.winner)
  const myIndex = state.players.findIndex((p) => p.id === me?.id)

  return (
    <div className="page winner-page">
      <div className="winner-card">
        <div className="crown">👑</div>
        <h1>{winner?.name} wins!</h1>
        <p className="sub">The last player standing — everyone else crawled away 🪳</p>

        <div className="standings">
          {state.players.map((p, i) => (
            <div key={p.id} className={`standing ${p.out ? 'lost' : 'won'}`}>
              <span className="avatar">{AVATARS[(i - myIndex + state.players.length) % state.players.length]}</span>
              <span className="pname">{p.name}{p.id === me?.id && ' (you)'}</span>
              <span className="faceup">
                {p.faceUp.map((c) => creatureById(c.creature).emoji).join(' ')}
              </span>
              <span className="tag">{p.out ? '💀 eliminated' : '🏆 winner'}</span>
            </div>
          ))}
        </div>

        <div className="btn-row">
          <button className="btn gold big" onClick={() => update((s) => startGame(s))}>
            🔁 Play Again
          </button>
          <button className="btn ghost" onClick={exit}>🏠 Back to Home</button>
        </div>

        <div className="log">
          {state.log.slice(-5).map((l, i) => <div key={i}>{l}</div>)}
        </div>
      </div>
    </div>
  )
}
