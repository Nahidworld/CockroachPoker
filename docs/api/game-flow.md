# Game Flow and End-to-End Walkthrough

This page maps the Cockroach Poker rules to the actual backend calls and walks through a complete
game.

## State machine

```text
WAITING ──start (host, 2–6 players, 64 cards dealt)──► IN_PROGRESS ──losing condition──► FINISHED
```

- `WAITING`: players can join; `play`/`peek`/`pass`/`challenge` are rejected.
- `IN_PROGRESS`: exactly one current player; at most one pending card.
- `FINISHED`: no further actions; `currentPlayerId` is `null` and `loserPlayerId` is set.

## Rules → API mapping

| Rule | Enforced where |
|---|---|
| 2–6 players | `POST /start` (`Invalid game state`, 409) |
| Host starts the game | `POST /start` (`Not room host`, 403) |
| All 64 cards dealt evenly | `POST /start` |
| Only the current player may play | `play` (`Invalid game state`, 409) |
| Played card must be in the player's hand | `play` (`Invalid request`, 400) |
| Target must be another room member | `play` (`Invalid request`, 400) |
| New round starts without a pending card | `play` (`Invalid game state`, 409) |
| Only the current receiver may peek | `peek` (`Invalid game state`, 409) |
| A receiver can peek only once | `peek` (`Invalid game state`, 409) |
| Only the current receiver may pass | `pass` (`Invalid game state`, 409) |
| Receiver must peek before passing | `pass` (`Invalid game state`, 409) |
| A player who has seen the card cannot receive it again | `pass` (`Invalid request`, 400) |
| The last eligible player must challenge | `pass` (`Invalid game state`, 409) when no eligible target remains |
| Only the current receiver may challenge | `challenge` (`Invalid game state`, 409) |
| Correct challenge → latest claimant collects | `challenge` resolution |
| Incorrect challenge → challenger collects | `challenge` resolution |
| Recipient starts the next round | `challenge` resolution |
| Four of one creature → loss | `challenge` resolution, on the recipient |
| Empty hand at round start → loss | `challenge` resolution, on the recipient |

## Challenge truth table

`guess = true` means "the declaration is truthful". `guess = false` means "the declaration is a
bluff".

| Declaration truthful? | `guess` | Challenge correct? | Card recipient |
|---|---|---|---|
| Yes | `true` | Yes | Latest claimant |
| Yes | `false` | No | Challenger |
| No | `true` | No | Challenger |
| No | `false` | Yes | Latest claimant |

Notes:

- The **latest claimant** is whoever last declared or passed the card, not necessarily the original
  sender.
- The recipient collects the card face-up and starts the next round.
- The losing check is applied only to that recipient, at the start of the next round.
- The challenger does not need to peek first.

## End-to-end walkthrough

The scenario uses three players:

| Player | ID (illustrative) |
|---|---|
| Alice (host) | `11111111-1111-1111-1111-111111111111` |
| Bob | `22222222-2222-2222-2222-222222222222` |
| Carol | `33333333-3333-3333-3333-333333333333` |

> Player IDs and tokens are returned by the create/join responses, and each player's hand is
> available from `GET /api/rooms/{roomCode}/me`. The IDs below would be read from those responses
> rather than hard-coded.

### 1. Create and join the room (REST)

```bash
# -> 201 {"roomCode":"ABC123","playerNames":["Alice"],"playerCount":1,"maxPlayers":6,
#         "status":"WAITING","playerId":"11111111-1111-1111-1111-111111111111",
#         "playerToken":"<alice-token>"}
curl -s -X POST http://localhost:8080/api/rooms \
  -H 'Content-Type: application/json' \
  -d '{"playerName":"Alice"}'

# Each join response returns the joining player's own playerId and playerToken.
curl -s -X POST http://localhost:8080/api/rooms/ABC123/join \
  -H 'Content-Type: application/json' \
  -d '{"playerName":"Bob"}'

curl -s -X POST http://localhost:8080/api/rooms/ABC123/join \
  -H 'Content-Type: application/json' \
  -d '{"playerName":"Carol"}'

# Every player ID is public:
curl -s http://localhost:8080/api/rooms/ABC123

# Each player reads their own hand with their token:
curl -s http://localhost:8080/api/rooms/ABC123/me \
  -H 'X-Player-Token: <bob-token>'
```

Each join publishes `PLAYER_JOINED` to `/topic/rooms/ABC123`.

### 2. Subscribe (WebSocket)

Before starting, subscribe to:

```text
/topic/rooms/ABC123
/user/queue/peek
/user/queue/errors
```

### 3. Start the game (REST)

```bash
curl -s -X POST http://localhost:8080/api/rooms/ABC123/start \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"11111111-1111-1111-1111-111111111111"}'
```

Response:

```json
{
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "players": [
    { "playerId": "11111111-1111-1111-1111-111111111111", "playerName": "Alice" },
    { "playerId": "22222222-2222-2222-2222-222222222222", "playerName": "Bob" },
    { "playerId": "33333333-3333-3333-3333-333333333333", "playerName": "Carol" }
  ],
  "playerNames": ["Alice", "Bob", "Carol"],
  "currentPlayerId": "11111111-1111-1111-1111-111111111111",
  "playerCount": 3
}
```

Event:

```json
{
  "type": "GAME_STARTED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "11111111-1111-1111-1111-111111111111",
  "currentPlayerId": "11111111-1111-1111-1111-111111111111",
  "message": "Game started"
}
```

Alice is the first player and therefore starts. All 64 cards are dealt: 22 / 21 / 21.

### 4. Alice plays a card to Bob (WebSocket)

Alice plays a real `FLY` but declares `BAT` (a bluff):

```json
{
  "destination": "/app/rooms/ABC123/play",
  "body": {
    "playerId": "11111111-1111-1111-1111-111111111111",
    "cardId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "targetPlayerId": "22222222-2222-2222-2222-222222222222",
    "declaredCreature": "BAT"
  }
}
```

Events:

```json
{
  "type": "CARD_PASSED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "11111111-1111-1111-1111-111111111111",
  "targetPlayerId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "seenPlayerIds": ["11111111-1111-1111-1111-111111111111"],
  "message": "A card was passed"
}
```

```json
{
  "type": "TURN_CHANGED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Turn changed"
}
```

Alice's hand is now one card smaller. The card is in flight and its real type is hidden.

### 5. Bob peeks (WebSocket, private)

```json
{
  "destination": "/app/rooms/ABC123/peek",
  "body": { "playerId": "22222222-2222-2222-2222-222222222222" }
}
```

Private message on `/user/queue/peek`:

```json
{
  "roomCode": "ABC123",
  "playerId": "22222222-2222-2222-2222-222222222222",
  "actualCreature": "FLY"
}
```

No public event is published. Bob is now allowed to pass.

### 6. Bob passes to Carol (WebSocket)

```json
{
  "destination": "/app/rooms/ABC123/pass",
  "body": {
    "playerId": "22222222-2222-2222-2222-222222222222",
    "targetPlayerId": "33333333-3333-3333-3333-333333333333"
  }
}
```

Events:

```json
{
  "type": "CARD_PASSED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "22222222-2222-2222-2222-222222222222",
  "targetPlayerId": "33333333-3333-3333-3333-333333333333",
  "declaredCreature": "BAT",
  "currentPlayerId": "33333333-3333-3333-3333-333333333333",
  "seenPlayerIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ],
  "message": "A card was passed"
}
```

```json
{
  "type": "TURN_CHANGED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "currentPlayerId": "33333333-3333-3333-3333-333333333333",
  "message": "Turn changed"
}
```

Bob is the latest claimant. Carol is the current receiver. Alice and Bob cannot receive this card
again because they have seen it.

### 7. Carol challenges (WebSocket)

Carol calls the bluff (`guess: false`):

```json
{
  "destination": "/app/rooms/ABC123/challenge",
  "body": {
    "playerId": "33333333-3333-3333-3333-333333333333",
    "guess": false
  }
}
```

The declaration was `BAT`, the actual card is `FLY`, so the declaration was false and Carol's guess
was false → the challenge is **correct**. The latest claimant, Bob, collects the card.

Events:

```json
{
  "type": "CHALLENGE_RESOLVED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "33333333-3333-3333-3333-333333333333",
  "targetPlayerId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT",
  "creatureType": "FLY",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Challenge was correct"
}
```

```json
{
  "type": "CARD_COLLECTED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "22222222-2222-2222-2222-222222222222",
  "creatureType": "FLY",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Bob collected a FLY (1 total)"
}
```

```json
{
  "type": "TURN_CHANGED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Turn changed"
}
```

Bob starts the next round. Because Bob still has cards and has only one `FLY`, the game continues.

### 8. Game end

The game ends when the recipient of a challenge:

- collects a fourth card of the same creature type, or
- has no cards left in hand.

The final sequence is `CHALLENGE_RESOLVED` → `CARD_COLLECTED` → `GAME_FINISHED`:

```json
{
  "type": "GAME_FINISHED",
  "roomCode": "ABC123",
  "status": "FINISHED",
  "actorPlayerId": "22222222-2222-2222-2222-222222222222",
  "loserPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Bob lost the game"
}
```

`currentPlayerId` is omitted because it is `null` after the game finishes.

## REST equivalents

`play`, `peek`, `pass`, and `challenge` also exist under `/api/rooms/{roomCode}/...` with identical
request bodies and validation. Important differences:

- REST returns the DTO directly instead of sending a private message.
- REST gameplay calls do **not** publish WebSocket events. If some clients use REST and others
  listen on the topic, the listeners will not be notified.
- Private peek data is returned in the HTTP response, not on `/user/queue/peek`.

## Resetting a game

There is no reset or rematch endpoint. Start a new room.
