# Known Limitations and Caveats

These are properties of the current backend, not documentation bugs. Read this before building a
client against the API.

## Summary

| # | Gap | Severity | Impact |
|---|---|---|---|
| 1 | No hand or card ID exposure | High | A client cannot choose a `cardId`, so it cannot play a card |
| 2 | No game-state snapshot or event replay | High | Reconnecting or late-joining clients cannot reconstruct the game |
| 3 | REST gameplay endpoints publish no WS events | High | Mixed REST + WebSocket clients see inconsistent state |
| 4 | No authentication; `playerId` is trusted | High | Any caller can act as another player |
| 5 | No REST CORS configuration | Medium | Browser apps on another origin are blocked |
| 6 | No room cleanup or expiry | Medium | In-memory rooms accumulate forever |
| 7 | No persistence | Medium | Every room is lost on restart |
| 8 | No Bean Validation | Low | Malformed bodies may produce generic framework errors |
| 9 | No structured collected-card counts | Low | Opponent piles can only be inferred from event message text |
| 10 | No leave/kick/rematch/spectator support | Low | A player who disconnects can stall the game |
| 11 | Single-instance, in-memory broker | Low | Cannot scale horizontally |

## Details

### 1. No hand or card ID exposure

`PlayCardRequest.cardId` must reference a card in the player's hand, but no endpoint or event
returns hands or card IDs. Peek reveals only the pending card's creature type.

Player IDs **are** exposed (`RoomResponse.playerId` on create/join and `RoomResponse.players` /
`GameResponse.players`), so rooms and turns can be addressed. Hands remain the blocking gap.

There is no workaround with the current API.

**Suggested fix:** add a private player view (REST or `/user` queue) containing:

- `playerId`, `name`, `isCurrentPlayer`
- the caller's hand as `{cardId, creatureType}` (or opaque card IDs if the client should not see types)
- collected counts per creature type
- current pending-card summary and eligible targets

### 2. No state snapshot or replay

Clients learn state only from events published after they subscribe. There is no
`GET /api/rooms/{code}/state`, no WS snapshot command, and no `@SubscribeMapping`. Events are not
retained or replayed.

Consequences:

- A client that reloads or reconnects cannot recover the pending card, turn, hands, or collected
  piles.
- A late subscriber misses `PLAYER_JOINED` and `GAME_STARTED`.
- Public events expose only IDs, so a reconnecting client cannot render names without get-room.

**Suggested fix:** publish a private snapshot on subscription (or add a state endpoint) with public
state plus the caller's private view.

### 3. REST gameplay mutates state without publishing events

`join` and `start` publish WS events, but `play`, `challenge`, `peek`, and `pass` do not when called
over REST.

Impact: if any client uses the REST gameplay endpoints, WebSocket subscribers are never notified and
their local state becomes stale. `peek` over REST also bypasses `/user/queue/peek`.

**Suggested workaround:** use WebSocket commands for all gameplay and REST only for
create/join/start/get.

**Suggested fix:** publish the same events from the REST controller, or remove/disable the REST
gameplay endpoints and make WebSocket the only gameplay path.

### 4. No authentication or authorization

- `playerId` is supplied by the client and only checked for room membership.
- The WebSocket principal is an anonymous per-session UUID and is not linked to any player.
- Private peek results are sent to the session that sent the command, not to a verified player.
- Anyone who knows a valid `playerId` can act as that player, including peeking at their card.

Exposing player IDs in room responses makes them easier to obtain, but it does not change the trust
model: IDs were never secret, and they are not credentials.

This is intentional for the hackathon MVP, but the API must not be exposed publicly as-is.

**Suggested fix:** issue a signed player token at join time and bind it to the session; validate the
token on every command and on private-queue delivery.

### 5. No REST CORS configuration

The WebSocket endpoint allows all origins, but REST has no CORS configuration. A browser app served
from a different origin (for example a Vite dev server on port 5173) will be blocked when calling
`http://localhost:8080/api/rooms`.

Workarounds:

- Use a dev proxy that forwards `/api` and `/ws` to `localhost:8080`.
- Serve the frontend from the same origin as the backend.

**Suggested fix:** add a `CorsConfigurationSource` / `WebMvcConfigurer#addCorsMappings` for `/api/**`
with the allowed frontend origins.

### 6. No room cleanup

Rooms are stored in a `ConcurrentHashMap` and are never removed, even after a game finishes or all
players disconnect. Long-running deployments will leak memory.

**Suggested fix:** TTL/expiry for `WAITING` and `FINISHED` rooms, plus a scheduled cleanup task.

### 7. No persistence

All state is in memory. A restart loses every room, game, hand, and pending card. There is no
database, event log, or recovery mechanism.

This is by design for the MVP.

### 8. No Bean Validation

Request records have no `jakarta.validation` annotations and controllers do not use `@Valid`.
Requiredness is enforced manually in `RoomService`. Handled validation failures produce
`ProblemDetail`, but malformed JSON and other framework-level failures use Spring's default error
body instead.

**Suggested fix:** add `spring-boot-starter-validation`, annotate DTOs, and add handlers for
`HttpMessageNotReadableException` / `MethodArgumentNotValidException`.

### 9. Collected cards are not structured

`CARD_COLLECTED` includes the collected `creatureType` and a human-readable count in `message`
(for example `"Bob collected a FLY (1 total)"`). There is no structured per-creature count per
player, and `RoomResponse`/`GameResponse` do not include piles.

Clients should not parse `message` for game logic.

**Suggested fix:** include collected counts in a player view or in the event payload.

### 10. No leave, kick, rematch, or spectator support

- There is no endpoint to leave a room, remove a player, or restart the game.
- A disconnected player's turn cannot be skipped or timed out, so the game can stall.
- There is no spectator/public view of hands or piles.
- Starting a new game requires creating a new room.

### 11. Delivery and scaling caveats

- Events are published after the state change; if publishing fails, the state has already changed.
  There is no outbox or retry.
- The simple broker is in-memory; there is no external broker, shared session registry, or cluster
  support.
- Game mutations are serialized per room with `synchronized (room)`, which assumes a single JVM.
- No rate limiting, idempotency keys, or command acknowledgements.
- Room codes are 6 random alphanumeric characters; collisions are retried on creation.

## Recommended follow-up order

1. **Unblock a playable client:** add a private hand/state view (item 1). Player IDs are already
   exposed on create/join, so this is the remaining blocker.
2. **Consistency:** add CORS/proxy support and make REST gameplay publish events or be removed
   (items 3 and 5).
3. **Resilience:** add a state snapshot on connect/reconnect (item 2).
4. **Security:** add a player token bound to the session (item 4).
5. **Operations:** add room TTL and persistence if deployment continues beyond the MVP (items 6–7).
