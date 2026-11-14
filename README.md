# 🪳 Cockroach Poker

A real-time multiplayer web version of the Cockroach Poker bluffing card game.
2–6 players · 64 cards · 8 creature types.

## Tech stack

- **Backend:** Java 21, Spring Boot 4, Maven, Spring WebSocket + STOMP, Lombok, JUnit 5
- **Frontend:** React 18 + Vite 5, `@stomp/stompjs`
- **Communication:** REST for rooms, STOMP over WebSocket for gameplay
- **Storage:** in memory (no database). Rooms are lost when the backend restarts.

The backend owns all game rules and state; the frontend only shows each player's view and
sends their actions.

## Prerequisites

- **Java 21** (`java -version`). Maven is not needed — the project includes the Maven wrapper.
- **Node.js 18 or newer** with npm (`node -v`).

## Run the game (backend + frontend)

Use two terminals, both starting from the repository root.

**Terminal 1 — backend** (runs on http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run
```

On Windows (PowerShell or cmd) use `.\mvnw.cmd spring-boot:run` instead.
Wait for `Started BackendApplication` in the output.

**Terminal 2 — frontend** (runs on http://localhost:5173):

```bash
cd frontend
npm install
npm run dev
```

`npm install` is only needed the first time and after dependencies change.

Then open **http://localhost:5173**. The Vite dev server forwards `/api` and `/ws` to the
backend, so no CORS or URL setup is needed — always open the frontend's address, not 8080.

### Play

1. One player enters a name, clicks **Create Room** and shares the 6-character room code.
2. The others enter a name, click **Join Room** and type the code.
3. The host clicks **Start Game** once 2–6 players are in (the game starts automatically at 6).

To test multiplayer on one computer, open the site in several browser tabs: each tab is its
own player. Reloading a tab keeps you in your game.

### Play with others on the same network

Start the frontend with `--host` so other devices can reach it:

```bash
npm run dev -- --host
```

Vite prints a `Network:` address (for example `http://192.168.1.20:5173`). Others on the
same Wi-Fi open that address. The backend can stay on localhost — only the frontend talks to
it. Your firewall may ask to allow Node.js.

### Backend on a different port or machine

Point the frontend at it with `BACKEND_URL` (default `http://localhost:8080`):

```bash
BACKEND_URL=http://localhost:9090 npm run dev
```

In PowerShell: `$env:BACKEND_URL="http://localhost:9090"; npm run dev`.

To run the backend itself on another port:
`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`.

## Tests

```bash
cd backend
./mvnw test
```

(Windows: `.\mvnw.cmd test`.) To check that the frontend compiles: `cd frontend` then
`npm run build`.

## Troubleshooting

- **"Cannot reach the game server"** — the backend isn't running, or is still starting.
  Start it (Terminal 1) and try again.
- **"That room no longer exists"** — the backend was restarted, which clears all rooms.
  Create a new room.
- **Port already in use** — another copy is still running. Stop it, or use another port as
  shown above.

## Project layout

```text
backend/    Spring Boot server: rooms, game rules, REST + WebSocket API
frontend/   React app (see frontend/README.md for how it talks to the backend)
AGENTS.md   Rules and conventions for contributors and coding agents
PROJECT.md  Project plan
```

## Game rules

- 64 cards: Cockroach, Stink Bug, Bat, Fly, Toad, Rat, Scorpion and Spider, 8 of each.
  All cards are dealt as evenly as possible; extra cards go to the first players.
- On your turn, slide a card face-down to another player and claim a creature — truthfully
  or not.
- The receiver either calls it (says the claim is TRUE or a BLUFF), or secretly peeks and
  passes it to someone who hasn't seen it, with their own claim. Once you peek you must pass;
  if nobody is left to pass to, you can't peek and must call it.
- Right call: the latest claimer takes the card face-up. Wrong call: the caller takes it.
  Whoever takes the card starts the next round.
- You lose, and the game ends, when you collect 4 cards of one creature, or when you must
  start a round with no cards in your hand.
