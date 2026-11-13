// ============================================================
// Cockroach Poker — game engine (pure logic, no React)
// 64 cards · 8 creature types · 2–6 players
// ============================================================

export const CREATURES = [
  { id: 'cockroach',  name: 'Cockroach',  emoji: '🪳' },
  { id: 'rat',        name: 'Rat',        emoji: '🐀' },
  { id: 'stinkbug',   name: 'Stink Bug',  emoji: '🐛' },
  { id: 'fly',        name: 'Fly',        emoji: '🪰' },
  { id: 'scorpion',   name: 'Scorpion',   emoji: '🦂' },
  { id: 'spider',     name: 'Spider',     emoji: '🕷️' },
  { id: 'chameleon',  name: 'Chameleon',  emoji: '🦎' },
  { id: 'bat',        name: 'Bat',        emoji: '🦇' },
]

export const creatureById = (id) => CREATURES.find((c) => c.id === id)

export const AVATARS = ['🐻', '🐼', '🦊', '🐸', '🐵', '🐯']

export const CARDS_PER_CREATURE = 8 // 8 x 8 = 64 cards

// ---------- deck ----------

export function makeDeck() {
  const deck = []
  for (const c of CREATURES) {
    for (let i = 0; i < CARDS_PER_CREATURE; i++) {
      deck.push({ id: `${c.id}-${i}`, creature: c.id })
    }
  }
  return deck
}

export function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

const rand = (n) => Math.floor(Math.random() * n)
const pick = (arr) => arr[rand(arr.length)]

// ---------- rooms (localStorage) ----------

const roomKey = (code) => `cp_room_${code}`

export function loadRoom(code) {
  try {
    const raw = localStorage.getItem(roomKey(code))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function saveRoom(code, state) {
  localStorage.setItem(roomKey(code), JSON.stringify(state))
}

export function randomCode() {
  // every room gets a different 4-digit code
  let code
  do {
    code = String(1000 + Math.floor(Math.random() * 9000))
  } while (localStorage.getItem(roomKey(code)))
  return code
}

// ---------- state creation ----------

export function createRoomState(code, hostName) {
  return {
    code,
    phase: 'lobby', // lobby | start-pile | respond | gameover
    players: [makePlayer('p1', hostName, false, 0)],
    turn: null,
    responder: null,
    lastPasser: null,
    pileCard: null,          // the single card travelling around the table
    lastClaim: null,         // { by, creature }  (player id + declared creature)
    lastResolution: null,    // public result of the last bluff call
    winner: null,
    log: [`🏠 Room ${code} created by ${hostName}`],
  }
}

function makePlayer(id, name, isBot, seat) {
  return { id, name, isBot, seat, hand: [], faceUp: [], out: false }
}

export function joinRoomState(s, name) {
  const id = 'p' + (s.players.length + 1)
  s.players.push(makePlayer(id, name, false, s.players.length))
  s.log.push(`👋 ${name} joined the room`)
  return s
}

export function addBot(s) {
  const names = ['Robo-Rat', 'Buggy Bot', 'Sir Bluff', 'Luck Bug', 'Botzilla']
  const taken = new Set(s.players.map((p) => p.name))
  const name = names.find((n) => !taken.has(n)) || `Bot ${s.players.length + 1}`
  s.players.push(makePlayer('b' + (s.players.length + 1), name, true, s.players.length))
  s.log.push(`🤖 ${name} (bot) joined the room`)
  return s
}

// ---------- game start / dealing ----------

export function startGame(s) {
  const deck = shuffle(makeDeck())
  const n = s.players.length
  const handSize = Math.floor(deck.length / n) // leftover cards are removed from the game
  s.players.forEach((p, i) => {
    p.hand = deck.slice(i * handSize, (i + 1) * handSize)
    p.faceUp = []
    p.out = false
  })
  s.phase = 'start-pile'
  s.turn = s.players[rand(n)].id
  s.pileCard = null
  s.lastClaim = null
  s.lastPasser = null
  s.responder = null
  s.lastResolution = null
  s.winner = null
  s.log = [`🎲 Game on! ${n} players, ${handSize} cards each.`]
  checkTurnStart(s)
  return s
}

// ---------- helpers ----------

export const alive = (s) => s.players.filter((p) => !p.out)
export const playerById = (s, id) => s.players.find((p) => p.id === id)

function nextAliveId(s, fromId) {
  const idx = s.players.findIndex((p) => p.id === fromId)
  for (let i = 1; i <= s.players.length; i++) {
    const p = s.players[(idx + i) % s.players.length]
    if (!p.out) return p.id
  }
  return null
}

// players the pile may legally be passed to (not yourself, not the one
// who just passed it to you — with 2 players this means you MUST call bluff)
export function legalPassTargets(s, responderId) {
  return alive(s).filter((p) => p.id !== responderId && p.id !== s.lastPasser)
}

// If it's someone's turn and their hand is empty -> they LOSE.
// If only one player is left -> winner.
export function checkTurnStart(s) {
  let guard = 0
  while (guard++ < 24) {
    const players = alive(s)
    if (players.length <= 1) {
      s.phase = 'gameover'
      s.winner = players[0] ? players[0].id : null
      s.turn = null
      s.responder = null
      s.log.push(`👑 ${players[0] ? players[0].name : '?'} is the last player standing — WINNER!`)
      return
    }
    const t = playerById(s, s.turn)
    if (!t || t.out) {
      s.turn = nextAliveId(s, t ? t.id : s.players[0].id)
      continue
    }
    if (t.hand.length === 0) {
      t.out = true
      s.log.push(`💀 ${t.name} has an empty hand — OUT!`)
      s.turn = nextAliveId(s, t.id)
      continue
    }
    return
  }
}

// ---------- actions ----------

// Current player puts a card from their hand face-down in front of the
// target player and declares (truthfully or not!) a creature.
export function startPile(s, playerId, cardId, creature, targetId) {
  const p = playerById(s, playerId)
  const target = playerById(s, targetId)
  const card = p.hand.find((c) => c.id === cardId)
  if (!card || !target) return s
  p.hand = p.hand.filter((c) => c.id !== cardId)
  s.pileCard = card
  s.lastClaim = { by: playerId, creature }
  s.lastPasser = playerId
  s.responder = targetId
  s.phase = 'respond'
  s.lastResolution = null
  const c = creatureById(creature)
  s.log.push(`${p.name} slides a face-down card to ${target.name} — claims it is ${c.emoji} ${c.name}`)
  return s
}

// Receiver blindly passes the pile to somebody else with a (new) claim.
export function passPile(s, responderId, targetId, creature) {
  const responder = playerById(s, responderId)
  const target = playerById(s, targetId)
  if (!responder || !target) return s
  s.lastClaim = { by: responderId, creature }
  s.lastPasser = responderId
  s.responder = targetId
  const c = creatureById(creature)
  s.log.push(`${responder.name} passes it on to ${target.name} — claims ${c.emoji} ${c.name}`)
  return s
}

// Receiver calls the bluff: card is revealed publicly.
//  - claim was TRUE  -> the CALLER takes the card face-up (bad!)
//  - claim was a BLUFF -> the LIAR takes the card face-up (bad!)
export function callBluff(s, callerId) {
  const card = s.pileCard
  const claim = s.lastClaim
  if (!card || !claim) return s
  const caller = playerById(s, callerId)
  const claimed = creatureById(claim.creature)
  const actual = creatureById(card.creature)
  const matched = card.creature === claim.creature
  const takerId = matched ? callerId : claim.by
  const taker = playerById(s, takerId)
  const liar = playerById(s, claim.by)

  taker.faceUp.push(card)
  s.lastResolution = {
    card,
    claim: { ...claim, byName: liar.name },
    callerName: caller.name,
    matched,
    takerName: taker.name,
  }
  s.log.push(
    `⚡ ${caller.name} calls BLUFF on ${liar.name}! Card was ${actual.emoji} ${actual.name}, claimed ${claimed.emoji} ${claimed.name} → ` +
      (matched ? 'claim was TRUE' : 'it WAS a bluff') + ` — ${taker.name} takes it 😖`
  )

  s.pileCard = null
  s.lastClaim = null
  s.lastPasser = null
  s.responder = null
  s.phase = 'start-pile'

  // four-of-a-kind face-up = instant loss
  for (const p of s.players) {
    if (p.out) continue
    const counts = {}
    for (const c of p.faceUp) counts[c.creature] = (counts[c.creature] || 0) + 1
    for (const cid in counts) {
      if (counts[cid] >= 4) {
        p.out = true
        const c = creatureById(cid)
        s.log.push(`💀 ${p.name} collected 4 × ${c.emoji} ${c.name} — OUT!`)
      }
    }
  }

  // whoever took the penalty card starts the next round
  s.turn = taker.out ? nextAliveId(s, taker.id) : taker.id
  checkTurnStart(s)
  return s
}

// ---------- bot AI ----------

export function botAct(s) {
  if (s.phase !== 'start-pile' && s.phase !== 'respond') return s
  const actorId = s.phase === 'start-pile' ? s.turn : s.responder
  const actor = playerById(s, actorId)
  if (!actor || !actor.isBot || actor.out) return s

  if (s.phase === 'start-pile') {
    // start a pile: random card, usually honest claim, random victim
    const card = pick(actor.hand)
    let creature = card.creature
    if (Math.random() > 0.65) {
      const others = CREATURES.filter((c) => c.id !== card.creature)
      creature = pick(others).id
    }
    const targets = alive(s).filter((p) => p.id !== actor.id)
    return startPile(s, actor.id, card.id, creature, pick(targets).id)
  }

  // responding: sometimes call bluff, otherwise pass on
  const targets = legalPassTargets(s, actor.id)
  if (targets.length === 0) return callBluff(s, actor.id) // cornered: must call
  if (Math.random() < 0.35) return callBluff(s, actor.id)
  const creature = pick(CREATURES).id
  return passPile(s, actor.id, pick(targets).id, creature)
}
