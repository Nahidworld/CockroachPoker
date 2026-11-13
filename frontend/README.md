# 🪳 Cockroach Poker — Frontend (React + Vite)

Party bluffing game · 2–6 players · 64 cards · 8 creature types

## Run it

```bash
npm install
npm run dev
```

Then open http://localhost:5173

## Pages / Flow

1. **Home** — input your name → **Create Room** (generates a random 4-digit code like `4673`,
   different every time) or **Join Room** with a code.
2. **Waiting Room** — shows the code to share. Friends can join by opening the site in another
   tab and entering the code (rooms sync via `localStorage`). You can also **Add Bot** to fill
   empty seats. The game **auto-starts when 6 players** have joined (or host starts at 2+).
3. **Game Table** — 64 cards (8 of each creature) are split evenly among players. On your turn:
   pick a card, declare a creature (truth or bluff), pick a player. They can **Peek**, **Pass**,
   or **Call Bluff**. Wrong caller takes the card face-up.
4. **Winner** — last player standing wins. You lose if you collect **4 of a kind** face-up,
   or if your **hand is empty** on your turn.

## Multiplayer note

This is the **frontend only** — rooms sync across browser tabs/windows on the same PC using
`localStorage`. To play over the internet you'd add a backend (e.g. Socket.io / Firebase) using
the same room-code + game-state structure in `src/game.js`.

## Rules implemented

- 2–6 players, 64 cards, 8 creatures (Cockroach, Rat, Stink Bug, Fly, Scorpion, Spider, Chameleon, Bat)
- Cards are split evenly among members at the start
- Peek and Pass, Call Bluff, bluffing via creature declarations
- Four-of-a-kind (face-up) = losing condition
- Empty hand on your turn = losing condition
- You can't pass the card back to the player who gave it to you
