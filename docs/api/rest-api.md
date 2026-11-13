# REST API

Base path: `/api/rooms`  
Base URL: `http://localhost:8080`  
Content type: `application/json`

REST is used for room management. The same gameplay actions are also exposed over
WebSocket/STOMP (see [websocket-api.md](./websocket-api.md)).

> **Event caveat:** only `join` and `start` publish WebSocket events. The REST gameplay
> endpoints (`play`, `challenge`, `peek`, `pass`) change state **silently** — they do not publish
> events to `/topic/rooms/{roomCode}`.

## Endpoint summary

| # | Method | Path | Purpose | Publishes WS event |
|---|---|---|---|---|
| 1 | `POST` | `/api/rooms` | Create a room | — |
| 2 | `POST` | `/api/rooms/{roomCode}/join` | Join a room | `PLAYER_JOINED` |
| 3 | `GET` | `/api/rooms/{roomCode}` | Read public room state | — |
| 4 | `POST` | `/api/rooms/{roomCode}/start` | Start the game (host only) | `GAME_STARTED` |
| 5 | `POST` | `/api/rooms/{roomCode}/play` | Play a card and declare a creature | — |
| 6 | `POST` | `/api/rooms/{roomCode}/challenge` | Challenge the declaration | — |
| 7 | `POST` | `/api/rooms/{roomCode}/peek` | Secretly inspect the pending card | — |
| 8 | `POST` | `/api/rooms/{roomCode}/pass` | Pass the inspected card to another player | — |

## 1. Create a room

`POST /api/rooms` → `201 Created`

Request:

```json
{
  "playerName": "Alice"
}
```

Response `RoomResponse`:

```json
{
  "roomCode": "ABC123",
  "playerId": "11111111-1111-1111-1111-111111111111",
  "players": [
    {
      "playerId": "11111111-1111-1111-1111-111111111111",
      "playerName": "Alice"
    }
  ],
  "playerNames": ["Alice"],
  "playerCount": 1,
  "maxPlayers": 6,
  "status": "WAITING"
}
```

The creator becomes the host and the first player. Every player receives a server-assigned
`playerId`; the creator's ID is returned both at the top level (`playerId`) and inside `players`.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | `playerName` is missing, blank, or whitespace |

## 2. Join a room

`POST /api/rooms/{roomCode}/join` → `200 OK`

Path parameter: `roomCode` — 6-character room code (case-insensitive).

Request:

```json
{
  "playerName": "Bob"
}
```

Response `RoomResponse`:

```json
{
  "roomCode": "ABC123",
  "playerId": "22222222-2222-2222-2222-222222222222",
  "players": [
    {
      "playerId": "11111111-1111-1111-1111-111111111111",
      "playerName": "Alice"
    },
    {
      "playerId": "22222222-2222-2222-2222-222222222222",
      "playerName": "Bob"
    }
  ],
  "playerNames": ["Alice", "Bob"],
  "playerCount": 2,
  "maxPlayers": 6,
  "status": "WAITING"
}
```

The joining player's `playerId` is returned at the top level and in `players`. Player IDs are
unique per player and are stable for the lifetime of the room.

Side effect: publishes a `PLAYER_JOINED` event to `/topic/rooms/{roomCode}`.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | `playerName` is missing, blank, or whitespace |
| `Room not found` | `404` | Unknown room code |
| `Room unavailable` | `409` | Room already has 6 players, or the game already started |

## 3. Get public room state

`GET /api/rooms/{roomCode}` → `200 OK`

Response `RoomResponse` (same shape as create/join, but `playerId` is `null` because `GET` has no
acting player). `players` still contains every player's ID.

| Error | Status | Cause |
|---|---|---|
| `Room not found` | `404` | Unknown room code |

Only public data is returned: room code, player IDs/names, counts, and status. Hands and collected
cards are never exposed here.

## 4. Start the game

`POST /api/rooms/{roomCode}/start` → `200 OK`

Request:

```json
{
  "playerId": "11111111-1111-1111-1111-111111111111"
}
```

Response `GameResponse`:

```json
{
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "players": [
    {
      "playerId": "11111111-1111-1111-1111-111111111111",
      "playerName": "Alice"
    },
    {
      "playerId": "22222222-2222-2222-2222-222222222222",
      "playerName": "Bob"
    }
  ],
  "playerNames": ["Alice", "Bob"],
  "currentPlayerId": "11111111-1111-1111-1111-111111111111",
  "playerCount": 2
}
```

Rules enforced:

- Only the host can start the game.
- The room must be in `WAITING` status.
- The room must contain 2–6 players.
- All 64 cards are distributed as evenly as possible (see [data-models.md](./data-models.md)).
- The first player in player-list order (the host) starts.

Side effect: publishes a `GAME_STARTED` event to `/topic/rooms/{roomCode}`.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | `playerId` is missing or blank |
| `Not room host` | `403` | Caller is not the host |
| `Room not found` | `404` | Unknown room code |
| `Room unavailable` | `409` | Game already started |
| `Invalid game state` | `409` | Fewer than 2 players (or more than 6) |

## 5. Play a card

`POST /api/rooms/{roomCode}/play` → `200 OK`

Request `PlayCardRequest`:

```json
{
  "playerId": "11111111-1111-1111-1111-111111111111",
  "cardId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "targetPlayerId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT"
}
```

`declaredCreature` may be truthful or a bluff; the server does not reveal which.

Response `PendingCardResponse`:

```json
{
  "roomCode": "ABC123",
  "originalSenderId": "11111111-1111-1111-1111-111111111111",
  "latestClaimantId": "11111111-1111-1111-1111-111111111111",
  "declaredCreature": "BAT",
  "seenPlayerIds": ["11111111-1111-1111-1111-111111111111"],
  "currentReceiverId": "22222222-2222-2222-2222-222222222222"
}
```

The response never includes the card's real creature type or card ID.

Validation:

- Game must be `IN_PROGRESS`.
- No pending card may already exist.
- It must be the sender's turn.
- The sender must own `cardId`.
- The target must be a member of the room and must not be the sender.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | Blank ID, null `declaredCreature`, card not in hand, self-target, or target not in room |
| `Room not found` | `404` | Unknown room code |
| `Invalid game state` | `409` | Not in progress, pending card already exists, or not the sender's turn |

## 6. Challenge the declaration

`POST /api/rooms/{roomCode}/challenge` → `200 OK`

Request `ChallengeRequest`:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222",
  "guess": true
}
```

`guess` semantics:

- `true` — "the declaration is truthful".
- `false` — "the declaration is a bluff".

Only the current receiver may challenge. Peeking first is not required.

Response `ChallengeResponse`:

```json
{
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "challengerId": "22222222-2222-2222-2222-222222222222",
  "correct": true,
  "recipientId": "11111111-1111-1111-1111-111111111111",
  "declaredCreature": "BAT",
  "actualCreature": "BAT",
  "currentPlayerId": "11111111-1111-1111-1111-111111111111",
  "loserPlayerId": null
}
```

Resolution:

- `correct = (guess == declarationIsTruthful)`.
- Correct challenge → `recipientId` is the **latest claimant**, not necessarily the original sender.
- Incorrect challenge → `recipientId` is the challenger.
- The recipient collects the card face-up and starts the next round.
- If that starts a losing condition, `status` is `FINISHED`, `loserPlayerId` is set, and
  `currentPlayerId` is `null`.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | Blank `playerId` or missing `guess` |
| `Room not found` | `404` | Unknown room code |
| `Invalid game state` | `409` | Not in progress, no pending card, or caller is not the receiver |

## 7. Peek at the pending card

`POST /api/rooms/{roomCode}/peek` → `200 OK`

Request:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222"
}
```

Response `PeekResponse`:

```json
{
  "roomCode": "ABC123",
  "playerId": "22222222-2222-2222-2222-222222222222",
  "actualCreature": "FLY"
}
```

Only the current receiver may peek, and only once per pending card. Peeking does not pass the
card; it only makes the player eligible to pass and adds them to `seenPlayerIds`.

> This endpoint is private in intent, but because there is no authentication, any caller who knows
> a receiver's `playerId` can request the peek. Prefer the WebSocket private queue for real clients.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | Blank `playerId` |
| `Room not found` | `404` | Unknown room code |
| `Invalid game state` | `409` | Not in progress, no pending card, caller is not the receiver, or already peeked |

## 8. Pass the pending card

`POST /api/rooms/{roomCode}/pass` → `200 OK`

Request `PassCardRequest`:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222",
  "targetPlayerId": "33333333-3333-3333-3333-333333333333"
}
```

Response `PendingCardResponse`:

```json
{
  "roomCode": "ABC123",
  "originalSenderId": "11111111-1111-1111-1111-111111111111",
  "latestClaimantId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT",
  "seenPlayerIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ],
  "currentReceiverId": "33333333-3333-3333-3333-333333333333"
}
```

Rules enforced:

- Only the current receiver may pass.
- The receiver must peek before passing.
- The target must be a room member, not the sender, and must not have seen the card.
- If no eligible target remains, the receiver must challenge instead.

| Error | Status | Cause |
|---|---|---|
| `Invalid request` | `400` | Blank ID, self-target, target not in room, or target already saw the card |
| `Room not found` | `404` | Unknown room code |
| `Invalid game state` | `409` | Not in progress, no pending card, not the receiver, receiver has not peeked, or no eligible target exists |

> The "no eligible target" check runs before self/membership/seen checks. In a two-player game the
> only other player is the original sender, who has already seen the card, so `pass` always returns
> `409` there. The receiver must challenge.

## Error format

Handled exceptions return RFC 9457 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Invalid game state",
  "status": 409,
  "detail": "It is not this player's turn in room: ABC123"
}
```

Mapping:

| Exception | HTTP status | `title` |
|---|---|---|
| `RoomNotFoundException` | `404 Not Found` | `Room not found` |
| `RoomFullException` | `409 Conflict` | `Room unavailable` |
| `GameAlreadyStartedException` | `409 Conflict` | `Room unavailable` |
| `InvalidGameStateException` | `409 Conflict` | `Invalid game state` |
| `NotRoomHostException` | `403 Forbidden` | `Not room host` |
| `IllegalArgumentException` | `400 Bad Request` | `Invalid request` |
| Anything unhandled | `500 Internal Server Error` | Spring's default error body |

Notes:

- `detail` contains the server's message, which includes the room code and sometimes the player or
  card ID. It never contains a stack trace.
- Framework-level failures (for example malformed JSON) use Spring's default error body rather than
  `ProblemDetail`, because only the listed exception types are handled.
- There are no Bean Validation annotations; requiredness and format are enforced in the services.

## Curl walkthrough

```bash
# 1. Create
curl -s -X POST http://localhost:8080/api/rooms \
  -H 'Content-Type: application/json' \
  -d '{"playerName":"Alice"}'

# 2. Join
curl -s -X POST http://localhost:8080/api/rooms/ABC123/join \
  -H 'Content-Type: application/json' \
  -d '{"playerName":"Bob"}'

# 3. Read public state
curl -s http://localhost:8080/api/rooms/ABC123

# 4. Start (host playerId from the create response)
curl -s -X POST http://localhost:8080/api/rooms/ABC123/start \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"11111111-1111-1111-1111-111111111111"}'

# 5. Play
curl -s -X POST http://localhost:8080/api/rooms/ABC123/play \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"11111111-1111-1111-1111-111111111111",
       "cardId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
       "targetPlayerId":"22222222-2222-2222-2222-222222222222",
       "declaredCreature":"BAT"}'

# 6. Peek (receiver)
curl -s -X POST http://localhost:8080/api/rooms/ABC123/peek \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"22222222-2222-2222-2222-222222222222"}'

# 7. Pass (receiver, after peeking; requires a third eligible player)
curl -s -X POST http://localhost:8080/api/rooms/ABC123/pass \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"22222222-2222-2222-2222-222222222222",
       "targetPlayerId":"33333333-3333-3333-3333-333333333333"}'

# 8. Challenge (current receiver)
curl -s -X POST http://localhost:8080/api/rooms/ABC123/challenge \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"33333333-3333-3333-3333-333333333333","guess":false}'
```
