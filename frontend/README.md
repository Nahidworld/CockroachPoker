# 🪳 Cockroach Poker — Frontend (React + Vite)

Party bluffing game · 2–6 players · 64 cards · 8 creature types

The Spring Boot backend in `../backend` owns all game rules and state. This frontend only
shows the server's view of the game and sends player actions.

## Run it

The full instructions for running the backend and frontend together (including Windows
commands, playing over your local network and troubleshooting) are in the
[root README](../README.md#run-the-game-backend--frontend). In short:

```bash
# terminal 1 — backend on port 8080 (Windows: .\mvnw.cmd spring-boot:run)
cd backend
./mvnw spring-boot:run

# terminal 2 — frontend on port 5173
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` (REST) and `/ws` (STOMP WebSocket) to the
backend, so no CORS setup is needed. To use a backend elsewhere, set `BACKEND_URL`
(e.g. `BACKEND_URL=http://localhost:9090 npm run dev`).

To try multiplayer on one computer, open the site in several tabs: each tab is its own player.

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload on port 5173 (add `-- --host` to allow other devices) |
| `npm run build` | Production build into `dist/` |
| `npm run preview` | Serve the `dist/` build locally on port 4173 (uses the same backend proxy) |

## How it talks to the backend

| What | How |
|---|---|
| Create / join a room | `POST /api/rooms`, `POST /api/rooms/{code}/join` → returns your `playerId` and secret `playerToken` |
| Start the game (host) | `POST /api/rooms/{code}/start` with header `X-Player-Token` |
| Leave the room | `POST /api/rooms/{code}/leave` with header `X-Player-Token` (in a game this is a forfeit) |
| Your view of the game (hand, players, card in flight) | `GET /api/rooms/{code}/me` with header `X-Player-Token` |
| Play, peek, pass, challenge | STOMP `SEND /app/rooms/{code}/{play\|peek\|pass\|challenge}` with header `X-Player-Token` |
| Connect | STOMP `CONNECT` with headers `X-Room-Code` and `X-Player-Token`, so the server knows when you drop out |
| Room events | subscribe `/topic/rooms/{code}` — every event triggers a fresh `/me` |
| Your peek result / your errors | subscribe `/user/queue/peek`, `/user/queue/errors` |

Every action is checked against the player's secret token, so knowing someone's (public)
player ID is not enough to act as them or peek at their card.

The session (`roomCode`, `playerId`, `playerToken`) is kept in `sessionStorage`, so a page
reload returns you to your game. Rooms live in the backend's memory and disappear when it restarts.

## Files

- `src/api.js` — REST calls
- `src/socket.js` — STOMP connection for one room
- `src/game.js` — creature names/emoji and small display helpers
- `src/App.jsx` — session, server sync and page switching
- `src/pages/` — `Home`, `Lobby`, `Table`, `GameOver`

## Rules (enforced by the backend)

- Creatures: Cockroach, Rat, Stink Bug, Fly, Scorpion, Spider, Toad, Bat — 8 cards each.
- On your turn: slide a card face-down to another player and claim a creature (truth or bluff).
- The receiver either calls it — "TRUE" or "BLUFF" — or peeks and passes it on with their
  own claim to someone who hasn't seen it. After peeking you must pass; if nobody is left
  to pass to, you can't peek and must call it.
- Right call: the latest claimer takes the card face-up. Wrong call: the caller takes it.
  Whoever takes it starts the next round.
- You lose (and the game ends) when you collect 4 of one creature, or must start a round
  with an empty hand.
- Leaving a game in progress counts as a loss. If your connection drops, you have 60 seconds
  to come back (reloading the page is fine) before you are treated as having left.
