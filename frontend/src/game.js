// ============================================================
// Cockroach Poker — display helpers.
// The backend owns all game rules and state; this file only maps
// server data to what the UI shows.
// ============================================================

// Ids match the backend's CreatureType enum.
export const CREATURES = [
  { id: 'COCKROACH', name: 'Cockroach', emoji: '🪳' },
  { id: 'RAT',       name: 'Rat',       emoji: '🐀' },
  { id: 'STINK_BUG', name: 'Stink Bug', emoji: '🐛' },
  { id: 'FLY',       name: 'Fly',       emoji: '🪰' },
  { id: 'SCORPION',  name: 'Scorpion',  emoji: '🦂' },
  { id: 'SPIDER',    name: 'Spider',    emoji: '🕷️' },
  { id: 'TOAD',      name: 'Toad',      emoji: '🐸' },
  { id: 'BAT',       name: 'Bat',       emoji: '🦇' },
]

const UNKNOWN_CREATURE = { id: '?', name: 'Unknown', emoji: '❔' }

export const creatureById = (id) => CREATURES.find((c) => c.id === id) || UNKNOWN_CREATURE

export const AVATARS = ['🐻', '🐼', '🦊', '🐨', '🐵', '🐯']

export const MAX_PLAYERS = 6
export const MIN_PLAYERS = 2
export const LOSING_COUNT = 4

export const playerName = (view, id) =>
  view?.players.find((p) => p.id === id)?.name ?? 'Someone'

// Players who may still receive the pending card: not me, and not anyone who has seen it.
export function eligiblePassTargets(view) {
  const pending = view.pendingCard
  if (!pending) return []
  return view.players.filter(
    (p) => p.id !== view.playerId && !pending.seenPlayerIds.includes(p.id)
  )
}

export function countByCreature(cards) {
  const counts = {}
  for (const c of cards) counts[c] = (counts[c] || 0) + 1
  return counts
}
