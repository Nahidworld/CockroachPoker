# Data Models

This page lists every DTO, enum, and value rule the backend exposes.

All DTOs are Java records. IDs are UUID strings. Enum values are serialized as uppercase strings.
REST responses include `null` fields; WebSocket events omit them (`@JsonInclude(NON_NULL)`).

## Enums

### `GameStatus`

| Value | Meaning |
|---|---|
| `WAITING` | Room exists and is accepting players |
| `IN_PROGRESS` | Game is running |
| `FINISHED` | A player lost; no further actions are accepted |

### `CreatureType`

| Value | Cards in deck |
|---|---|
| `COCKROACH` | 8 |
| `STINK_BUG` | 8 |
| `BAT` | 8 |
| `FLY` | 8 |
| `TOAD` | 8 |
| `RAT` | 8 |
| `SCORPION` | 8 |
| `SPIDER` | 8 |

64 cards total.

### `GameEventType`

`PLAYER_JOINED`, `GAME_STARTED`, `CARD_PASSED`, `CHALLENGE_RESOLVED`, `CARD_COLLECTED`,
`TURN_CHANGED`, `GAME_FINISHED`.

## Requests

### `CreateRoomRequest`

Used by `POST /api/rooms`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerName` | `String` | Yes | Must not be null or blank |

### `JoinRoomRequest`

Used by `POST /api/rooms/{roomCode}/join`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerName` | `String` | Yes | Must not be null or blank |

### `StartGameRequest`

Used by `POST /api/rooms/{roomCode}/start`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerId` | `String` | Yes | Must not be blank and must be the host |

### `PlayCardRequest`

Used by `POST /api/rooms/{roomCode}/play` and `/app/rooms/{roomCode}/play`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerId` | `String` | Yes | Must be the current player and a room member |
| `cardId` | `String` | Yes | Must be a card in that player's hand |
| `targetPlayerId` | `String` | Yes | Another room member; must not equal `playerId` |
| `declaredCreature` | `CreatureType` | Yes | Any of the 8 creature types; may be truthful or a bluff |

### `ChallengeRequest`

Used by `POST /api/rooms/{roomCode}/challenge` and `/app/rooms/{roomCode}/challenge`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerId` | `String` | Yes | Must be the current receiver |
| `guess` | `Boolean` | Yes | `true` = declaration is truthful; `false` = declaration is a bluff |

### `PeekRequest`

Used by `POST /api/rooms/{roomCode}/peek` and `/app/rooms/{roomCode}/peek`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerId` | `String` | Yes | Must be the current receiver who has not yet peeked this card |

### `PassCardRequest`

Used by `POST /api/rooms/{roomCode}/pass` and `/app/rooms/{roomCode}/pass`.

| Field | Type | Required | Rules |
|---|---|---|---|
| `playerId` | `String` | Yes | Must be the current receiver who has peeked |
| `targetPlayerId` | `String` | Yes | Another room member who has not seen the card |

## Responses

### `PlayerResponse`

Returned inside `RoomResponse.players` and `GameResponse.players`.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `playerId` | `String` | No | Server-assigned UUID |
| `playerName` | `String` | No | Display name |

### `RoomJoinResponse`

Returned by create-room and join-room. It contains the public room information plus the **calling**
player's ID and secret token.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | 6-character code |
| `playerNames` | `List<String>` | No | Names in join order |
| `playerCount` | `int` | No | Current player count |
| `maxPlayers` | `int` | No | Always `6` |
| `status` | `GameStatus` | No | `WAITING` on success |
| `playerId` | `String` | No | The caller's server-assigned UUID |
| `playerToken` | `String` | No | Secret for `GET /api/rooms/{roomCode}/me`; never share it |

### `RoomResponse`

Returned by get-room.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | 6-character code |
| `playerId` | `String` | Yes | Always `null` for get-room (no acting player) |
| `players` | `List<PlayerResponse>` | No | Every player with a server-assigned ID, in join order |
| `playerNames` | `List<String>` | No | Names in join order (kept for convenience) |
| `playerCount` | `int` | No | Current player count |
| `maxPlayers` | `int` | No | Always `6` |
| `status` | `GameStatus` | No | `WAITING`, `IN_PROGRESS`, or `FINISHED` |

Does **not** include hands, tokens, or collected cards.

### `CardResponse`

Returned inside `PlayerViewResponse.hand` for the caller's own cards.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `String` | No | Card UUID |
| `creatureType` | `CreatureType` | No | The card's real creature type |

### `PlayerSummaryResponse`

Public per-player summary inside `PlayerViewResponse.players`.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `String` | No | Server-assigned player UUID |
| `name` | `String` | No | Display name |
| `handCount` | `int` | No | Number of cards in hand; never their contents |
| `collectedCards` | `List<CreatureType>` | No | Face-up collected creature types, in order |

### `PlayerViewResponse`

Returned by `GET /api/rooms/{roomCode}/me` to the holder of a valid `X-Player-Token`.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | |
| `status` | `GameStatus` | No | |
| `playerId` | `String` | No | The caller's ID |
| `hostPlayerId` | `String` | No | Host ID |
| `currentPlayerId` | `String` | Yes | Whose turn it is; `null` when not in progress |
| `hand` | `List<CardResponse>` | No | **Only the caller's** hand |
| `players` | `List<PlayerSummaryResponse>` | No | Every player's public summary |

Never includes other players' cards or any player's token.

### `GameResponse`

Returned by start-game.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | |
| `status` | `GameStatus` | No | `IN_PROGRESS` on success |
| `players` | `List<PlayerResponse>` | No | Every player with a server-assigned ID, in join order |
| `playerNames` | `List<String>` | No | Names in join order |
| `currentPlayerId` | `String` | Yes | The player whose turn it is |
| `playerCount` | `int` | No | |

### `PendingCardResponse`

Returned by play and pass.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | |
| `originalSenderId` | `String` | No | Player who first played the card |
| `latestClaimantId` | `String` | No | Last player who declared/passed the card |
| `declaredCreature` | `CreatureType` | No | Current declaration |
| `seenPlayerIds` | `List<String>` | No | Original sender plus everyone who peeked |
| `currentReceiverId` | `String` | No | Player who must peek, pass, or challenge |

Never includes the real creature type, the card ID, or the card object.

### `ChallengeResponse`

Returned by REST challenge.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | |
| `status` | `GameStatus` | No | `IN_PROGRESS` or `FINISHED` |
| `challengerId` | `String` | No | Player who challenged |
| `correct` | `boolean` | No | Whether the challenge was correct |
| `recipientId` | `String` | No | Player who collects the card |
| `declaredCreature` | `CreatureType` | No | What was declared |
| `actualCreature` | `CreatureType` | No | The revealed card |
| `currentPlayerId` | `String` | Yes | `null` when the game finished |
| `loserPlayerId` | `String` | Yes | `null` while the game continues |

### `PeekResponse`

Sent privately by peek (REST response or `/user/queue/peek`).

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `roomCode` | `String` | No | |
| `playerId` | `String` | No | The receiver who peeked |
| `actualCreature` | `CreatureType` | No | The real card type |

### `GameEventResponse`

Public event envelope sent to `/topic/rooms/{roomCode}`. Fields not listed for a given event are
**omitted** from the JSON.

| Field | Type | Notes |
|---|---|---|
| `type` | `GameEventType` | Event kind |
| `roomCode` | `String` | |
| `status` | `GameStatus` | Room status after the action |
| `actorPlayerId` | `String` | Meaning depends on the event |
| `targetPlayerId` | `String` | Receiver/recipient where applicable |
| `declaredCreature` | `CreatureType` | Only while a pending card exists |
| `creatureType` | `CreatureType` | Only after a card is revealed or collected |
| `currentPlayerId` | `String` | Player whose turn it is |
| `loserPlayerId` | `String` | Set when the game finishes |
| `seenPlayerIds` | `List<String>` | Populated on `CARD_PASSED` |
| `message` | `String` | Human-readable summary; do not parse it for game logic |

See [websocket-api.md](./websocket-api.md#field-matrix) for the per-event field matrix.

### `ErrorResponse`

WebSocket-only error payload sent to `/user/queue/errors`.

| Field | Type | Notes |
|---|---|---|
| `error` | `String` | Machine-readable code |
| `message` | `String` | Human-readable detail; no stack traces |

### `ProblemDetail` (REST errors)

Standard Spring/RFC 9457 object.

| Field | Type | Notes |
|---|---|---|
| `type` | `String` | Usually `about:blank` |
| `title` | `String` | Short category, e.g. `Invalid game state` |
| `status` | `int` | HTTP status |
| `detail` | `String` | Specific message |

## Privacy matrix

| Data | Where it is exposed |
|---|---|
| Room code, status, player names/count | `RoomJoinResponse`, `RoomResponse`, `GameResponse`, public events |
| Player IDs | `RoomJoinResponse.playerId`; `RoomResponse.players`; `GameResponse.players` and `currentPlayerId`; `PlayerViewResponse.playerId`/`hostPlayerId`/`currentPlayerId` and `players[].id`; request payloads; pending/challenge responses; event `actorPlayerId`/`targetPlayerId`/`currentPlayerId`/`loserPlayerId` |
| Host ID | `RoomJoinResponse.playerId` for the creator, `RoomResponse.players[0]`, `PlayerViewResponse.hostPlayerId`, or `GAME_STARTED.actorPlayerId` |
| Player tokens | **Only** in the create/join response for the owning player. Never in get-room, the private view, public events, or another player's response |
| Card IDs | Only the caller's own cards, via `GET /api/rooms/{roomCode}/me` (`hand[].id`). Never exposed for other players or for a pending card |
| Own hand | `GET /api/rooms/{roomCode}/me` (`hand`) |
| Opponents' hands | **Not exposed**; only `handCount` in `PlayerViewResponse.players` |
| Pending card's real creature | Only to the current receiver via peek, and to everyone after a challenge reveals it (`creatureType`) |
| Declared creature | `PendingCardResponse`, `CARD_PASSED`, `CHALLENGE_RESOLVED` |
| Who has seen the pending card | `PendingCardResponse.seenPlayerIds`, `CARD_PASSED.seenPlayerIds` |
| Collected cards | Structured in `PlayerViewResponse.players[].collectedCards`; also announced via `CARD_COLLECTED` (`creatureType` and a count in `message`) |

## Card distribution

All 64 cards are shuffled and dealt as evenly as possible. Remainder cards go to the first players
in player-list order.

| Players | Cards per player |
|---|---|
| 2 | 32, 32 |
| 3 | 22, 21, 21 |
| 4 | 16, 16, 16, 16 |
| 5 | 13, 13, 13, 13, 12 |
| 6 | 11, 11, 11, 11, 10, 10 |

The host is the first player and therefore the starting player.

## Losing conditions

Checked when a player is about to start the next round (the challenge recipient):

- They have **4 collected cards of the same creature type**.
- They have **no cards left in their hand**.

When either is true, the game becomes `FINISHED`, that player is the loser, and a `GAME_FINISHED`
event is published.

## Challenge resolution summary

`correct = (guess == declarationIsTruthful)`

| Correct? | Card recipient |
|---|---|
| Yes | Latest claimant |
| No | Challenger |

The recipient collects the card face-up and starts the next round. See
[game-flow.md](./game-flow.md#challenge-truth-table) for the full truth table.
