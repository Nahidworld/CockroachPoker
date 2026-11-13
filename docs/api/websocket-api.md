# WebSocket / STOMP API

The real-time API uses **STOMP over a native WebSocket** (no SockJS fallback).

## Connection

| Item | Value |
|---|---|
| WebSocket endpoint | `ws://localhost:8080/ws` |
| Application prefix | `/app` |
| Broker prefixes | `/topic`, `/queue` |
| User destination prefix | `/user` |
| Message format | JSON |
| Authentication | None |

Configuration source: `backend/src/main/java/com/cockroach/backend/config/WebSocketConfig.java`.

Every connection is assigned an anonymous, random per-session `Principal` (UUID). It exists only so
Spring can address `/user` destinations; it does **not** identify a player and is not checked
against the `playerId` in command payloads. See [limitations.md](./limitations.md).

## Destinations overview

### Client → server commands

| Destination | Payload | Success side effects |
|---|---|---|
| `/app/rooms/{roomCode}/play` | `PlayCardRequest` | `CARD_PASSED`, `TURN_CHANGED` |
| `/app/rooms/{roomCode}/challenge` | `ChallengeRequest` | `CHALLENGE_RESOLVED`, `CARD_COLLECTED`, then `TURN_CHANGED` or `GAME_FINISHED` |
| `/app/rooms/{roomCode}/peek` | `PeekRequest` | Private `PeekResponse` only |
| `/app/rooms/{roomCode}/pass` | `PassCardRequest` | `CARD_PASSED`, `TURN_CHANGED` |

Room membership is validated for every command. All game rules are delegated to `RoomService` and
are identical to the REST endpoints.

### Server → client subscriptions

| Destination | Payload | Scope |
|---|---|---|
| `/topic/rooms/{roomCode}` | `GameEventResponse` | Everyone in the room |
| `/user/queue/peek` | `PeekResponse` | Only the session that sent the peek command |
| `/user/queue/errors` | `ErrorResponse` | Only the session that sent the failed command |

Subscribe **before** sending commands. There is no replay or state snapshot: an event published
before a subscription is not delivered retroactively.

## Client example

```js
import { Client } from '@stomp/stompjs';

const roomCode = 'ABC123';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  reconnectDelay: 3000,
});

client.onConnect = () => {
  client.subscribe(`/topic/rooms/${roomCode}`, (message) => {
    console.log('event', JSON.parse(message.body));
  });

  client.subscribe('/user/queue/peek', (message) => {
    console.log('private peek', JSON.parse(message.body));
  });

  client.subscribe('/user/queue/errors', (message) => {
    console.error('error', JSON.parse(message.body));
  });

  client.publish({
    destination: `/app/rooms/${roomCode}/play`,
    body: JSON.stringify({
      playerId: '11111111-1111-1111-1111-111111111111',
      cardId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      targetPlayerId: '22222222-2222-2222-2222-222222222222',
      declaredCreature: 'BAT',
    }),
  });
};

client.activate();
```

## Commands

### Play — `/app/rooms/{roomCode}/play`

Payload `PlayCardRequest`:

```json
{
  "playerId": "11111111-1111-1111-1111-111111111111",
  "cardId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "targetPlayerId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT"
}
```

Validated exactly like `POST /api/rooms/{roomCode}/play`: game in progress, no pending card, sender's
turn, sender owns the card, target is another member.

On success, publishes in order:

1. `CARD_PASSED` — actor is the passer, `targetPlayerId` is the new receiver, `declaredCreature` is
   included, `creatureType` is **not**.
2. `TURN_CHANGED` — `currentPlayerId` is the new receiver.

### Challenge — `/app/rooms/{roomCode}/challenge`

Payload `ChallengeRequest`:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222",
  "guess": true
}
```

`guess = true` means "the declaration is truthful"; `guess = false` means "the declaration is a
bluff". Only the current receiver may challenge; peeking first is not required.

On success, publishes in order:

1. `CHALLENGE_RESOLVED` — includes the declared and actual creature, the challenge result message,
   the recipient, and the loser if the game ended.
2. `CARD_COLLECTED` — actor is the player who collected the card; `creatureType` is the revealed card.
3. `TURN_CHANGED` if the game continues, or `GAME_FINISHED` if the recipient lost.

### Peek — `/app/rooms/{roomCode}/peek`

Payload `PeekRequest`:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222"
}
```

Only the current receiver may peek, and only once per pending card. On success, a private
`PeekResponse` is sent to `/user/queue/peek` **for the session that sent the command**. No public
event is published.

```json
{
  "roomCode": "ABC123",
  "playerId": "22222222-2222-2222-2222-222222222222",
  "actualCreature": "FLY"
}
```

### Pass — `/app/rooms/{roomCode}/pass`

Payload `PassCardRequest`:

```json
{
  "playerId": "22222222-2222-2222-2222-222222222222",
  "targetPlayerId": "33333333-3333-3333-3333-333333333333"
}
```

Only the current receiver may pass, the receiver must have peeked, and the target must be another
member who has not seen the card. If no eligible target remains, the receiver must challenge.

On success, publishes in order:

1. `CARD_PASSED` — actor is the passer (which becomes the new latest claimant), target is the new
   receiver, `seenPlayerIds` includes everyone who has seen the card.
2. `TURN_CHANGED` — `currentPlayerId` is the new receiver.

## Events

All public events are `GameEventResponse` objects sent to `/topic/rooms/{roomCode}`. The Java DTO is
annotated with `@JsonInclude(NON_NULL)`, so **fields that do not apply are omitted**, not sent as
`null`.

### Field matrix

| Field | `PLAYER_JOINED` | `GAME_STARTED` | `CARD_PASSED` | `CHALLENGE_RESOLVED` | `CARD_COLLECTED` | `TURN_CHANGED` | `GAME_FINISHED` |
|---|---|---|---|---|---|---|---|
| `type` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `roomCode` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `status` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `actorPlayerId` | joiner | host | passer | challenger | collector | — | loser |
| `targetPlayerId` | — | — | new receiver | recipient | — | — | — |
| `declaredCreature` | — | — | ✓ | ✓ | — | — | — |
| `creatureType` | — | — | — | actual card | collected card | — | — |
| `currentPlayerId` | — | starting player | new receiver | recipient, omitted if finished | collector, omitted if finished | current player | — |
| `loserPlayerId` | — | — | — | omitted unless finished | — | — | loser |
| `seenPlayerIds` | — | — | ✓ | — | — | — | — |
| `message` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

`actorPlayerId` / `loserPlayerId` / `currentPlayerId` are the only places player IDs appear in public
events. `PLAYER_JOINED.actorPlayerId` and `GAME_STARTED.actorPlayerId` are currently the only ways a
client can discover a player ID without reading server state.

### Event examples

`PLAYER_JOINED`:

```json
{
  "type": "PLAYER_JOINED",
  "roomCode": "ABC123",
  "status": "WAITING",
  "actorPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Bob joined the room"
}
```

`GAME_STARTED`:

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

`CARD_PASSED`:

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

`CHALLENGE_RESOLVED` (challenge incorrect, game continues):

```json
{
  "type": "CHALLENGE_RESOLVED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "actorPlayerId": "22222222-2222-2222-2222-222222222222",
  "targetPlayerId": "22222222-2222-2222-2222-222222222222",
  "declaredCreature": "BAT",
  "creatureType": "FLY",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Challenge was incorrect"
}
```

`CARD_COLLECTED`:

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

`TURN_CHANGED`:

```json
{
  "type": "TURN_CHANGED",
  "roomCode": "ABC123",
  "status": "IN_PROGRESS",
  "currentPlayerId": "22222222-2222-2222-2222-222222222222",
  "message": "Turn changed"
}
```

`GAME_FINISHED`:

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

### Event ordering

Commands publish sequentially, and the in-memory broker delivers per-session messages in send
order. A failed command publishes **no** success events; it sends only an error to
`/user/queue/errors`.

| Command | Events |
|---|---|
| `play` | `CARD_PASSED`, `TURN_CHANGED` |
| `pass` | `CARD_PASSED`, `TURN_CHANGED` |
| `challenge` (game continues) | `CHALLENGE_RESOLVED`, `CARD_COLLECTED`, `TURN_CHANGED` |
| `challenge` (losing condition) | `CHALLENGE_RESOLVED`, `CARD_COLLECTED`, `GAME_FINISHED` |
| `peek` | private `PeekResponse` only |

`PLAYER_JOINED` and `GAME_STARTED` are published by the **REST** controller, not by a WS command.

## Private error queue

Failures are sent to `/user/queue/errors` for the session that issued the command:

```json
{
  "error": "INVALID_GAME_STATE",
  "message": "It is not this player's turn in room: ABC123"
}
```

| Code | Cause |
|---|---|
| `ROOM_NOT_FOUND` | Room code does not exist |
| `INVALID_GAME_STATE` | Rule violation (wrong turn, no pending card, wrong receiver, already peeked, pass before peek, no eligible target, etc.) |
| `INVALID_REQUEST` | Blank/missing IDs, player is not a room member, self-target, card not in hand, target not in room, target already saw the card, null `declaredCreature`, null `guess` |
| `INTERNAL_ERROR` | Unexpected server failure |

`ROOM_FULL`, `GAME_ALREADY_STARTED`, and `NOT_ROOM_HOST` are defined in the WS error mapper for
parity with REST, but WS commands never produce them because room creation/joining/starting are
REST-only.

## Known WS-specific caveats

- `playerId` is trusted from the payload and only checked for room membership, so one client can
  act as another player if it knows that player's ID.
- Private peek results are delivered to the **WebSocket session** that sent the command, but there
  is no binding between that session and the `playerId`, so `peek` for another member still returns
  that member's card.
- There is no reconnection snapshot, no event replay, and no server-side subscription registry.
- The simple broker is in-memory and single-instance.
