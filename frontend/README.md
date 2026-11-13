# 🪳 Cockroach Poker — Frontend (React + Vite)

Party bluffing game · 2–6 players · 64 cards · 8 creature types

The Spring Boot backend in `../backend` owns all game rules and state. This frontend only
shows the server's view of the game and sends player actions.

## Run it

Start the backend first (port 8080):

```bash
cd ../backend
./mvnw spring-boot:run
```

Then the frontend:

```bash
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` (REST) and `/ws` (STOMP WebSocket) to the
backend, so no CORS setup is needed. To use a backend elsewhere, set `BACKEND_URL`
(e.g. `BACKEND_URL=http://localhost:9090 npm run dev`).

To try multiplayer on one computer, open the site in several tabs: each tab is its own player.

## How it talks to the backend

| What | How |
|---|---|
| Create / join a room | `POST /api/rooms`, `POST /api/rooms/{code}/join` → returns your `playerId` and secret `playerToken` |
| Start the game (host) | `POST /api/rooms/{code}/start` |
| Your view of the game (hand, players, card in flight) | `GET /api/rooms/{code}/me` with header `X-Player-Token` |
| Play, peek, pass, challenge | STOMP `SEND /app/rooms/{code}/{play\|peek\|pass\|challenge}` |
| Room events | subscribe `/topic/rooms/{code}` — every event triggers a fresh `/me` |
| Your peek result / your errors | subscribe `/user/queue/peek`, `/user/queue/errors` |

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
