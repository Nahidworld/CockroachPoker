# Known Limitations and Caveats

These are properties of the current backend, not documentation bugs. Read this before building a
client against the API.

## Summary

| # | Gap | Severity | Impact |
|---|---|---|---|
| 1 | No game-state snapshot or event replay | High | Reconnecting or late-joining clients cannot reconstruct the game |
| 2 | REST gameplay endpoints publish no WS events | High | Mixed REST + WebSocket clients see inconsistent state |
| 3 | No authentication; `playerId` is still trusted | High | Any caller can act as another player on gameplay commands |
| 4 | No REST CORS configuration | Medium | Browser apps on another origin are blocked |
| 5 | No room cleanup or expiry | Medium | In-memory rooms accumulate forever |
| 6 | No persistence | Medium | Every room is lost on restart |
| 7 | No Bean Validation | Low | Malformed bodies may produce generic framework errors |
| 8 | No leave/kick/rematch/spectator support | Low | A player who disconnects can stall the game |
| 9 | Single-instance, in-memory broker | Low | Cannot scale horizontally |

## What is already covered

- **Player IDs** are returned on create/join (`RoomJoinResponse.playerId`) and in
  `RoomResponse.players` / `GameResponse.players`.
- **Hands and card IDs** are returned to the owning player by `GET /api/rooms/{roomCode}/me`.
- **Collected cards** are structured in `PlayerViewResponse.players[].collectedCards`.
- **Player tokens** keep one player from reading another player's hand through `/me`.

## Details

### 1. No state snapshot or replay

Clients learn state only from events published after they subscribe. There is no WS snapshot
command and no `@SubscribeMapping`. Events are not retained or replayed.

Consequences:

- A client that reloads or reconnects cannot recover the pending card or turn without calling
  `GET /api/rooms/{roomCode}` and `GET /api/rooms/{roomCode}/me`.
- A late subscriber misses `PLAYER_JOINED` and `GAME_STARTED`.
- Public events expose only IDs, so a reconnecting client cannot render names without get-room.

**Suggested fix:** publish a private snapshot on subscription (or add a dedicated state endpoint)
with public state plus the caller's private view.

### 2. REST gameplay mutates state without publishing events

`join` and `start` publish WS events, but `play`, `challenge`, `peek`, and `pass` do not when called
over REST.

Impact: if any client uses the REST gameplay endpoints, WebSocket subscribers are never notified and
their local state becomes stale. `peek` over REST also bypasses `/user/queue/peek`.

**Suggested workaround:** use WebSocket commands for all gameplay and REST only for
create/join/get/me/start.

**Suggested fix:** publish the same events from the REST controller, or remove/disable the REST
gameplay endpoints and make WebSocket the only gameplay path.

### 3. No authentication; `playerId` is still trusted

- The `playerToken` protects only `GET /api/rooms/{roomCode}/me`. Gameplay commands still identify
  the actor by `playerId` in the payload, which is only checked for room membership.
- The WebSocket principal is an anonymous per-session UUID and is not linked to any player.
- Anyone who knows a valid `playerId` can act as that player on `play`/`pass`/`peek`/`challenge`.
- Tokens are random UUIDs stored in memory and are not signed or rotated.

This is intentional for the hackathon MVP, but the API must not be exposed publicly as-is.

**Suggested fix:** validate the `playerToken` (or a signed equivalent) on every command, bind it to
the WebSocket session, and derive the acting player from the token instead of trusting `playerId`.

### 4. No REST CORS configuration

The WebSocket endpoint allows all origins, but REST has no CORS configuration. A browser app served
from a different origin (for example a Vite dev server on port 5173) will be blocked when calling
`http://localhost:8080/api/rooms`.

Workarounds:

- Use a dev proxy that forwards `/api` and `/ws` to `localhost:8080`.
- Serve the frontend from the same origin as the backend.

**Suggested fix:** add a `CorsConfigurationSource` / `WebMvcConfigurer#addCorsMappings` for `/api/**`
with the allowed frontend origins.

### 5. No room cleanup

Rooms are stored in a `ConcurrentHashMap` and are never removed, even after a game finishes or all
players disconnect. Long-running deployments will leak memory.

**Suggested fix:** TTL/expiry for `WAITING` and `FINISHED` rooms, plus a scheduled cleanup task.

### 6. No persistence

All game state is in memory. A restart loses every room, game, hand, token, and pending card. There
is no event log or recovery mechanism.

An in-memory H2 database and its web console (`/h2-console`) are configured, but nothing writes to
it: no JPA entities, repositories, or schema exist, so its `PUBLIC` schema is empty and it also loses
everything on restart. It is a development convenience, not persistence.

This is by design for the MVP.

### 7. No Bean Validation

Request records have no `jakarta.validation` annotations and controllers do not use `@Valid`.
Requiredness is enforced manually in `RoomService`. Handled validation failures produce
`ProblemDetail`, but malformed JSON and other framework-level failures use Spring's default error
body instead.

**Suggested fix:** add `spring-boot-starter-validation`, annotate DTOs, and add handlers for
`HttpMessageNotReadableException` / `MethodArgumentNotValidException`.

### 8. No leave, kick, rematch, or spectator support

- There is no endpoint to leave a room, remove a player, or restart the game.
- A disconnected player's turn cannot be skipped or timed out, so the game can stall.
- There is no spectator view. Every player can read only their own hand via `/me`.
- Starting a new game requires creating a new room.

### 9. Delivery and scaling caveats

- Events are published after the state change; if publishing fails, the state has already changed.
  There is no outbox or retry.
- The simple broker is in-memory; there is no external broker, shared session registry, or cluster
  support.
- Game mutations are serialized per room with `synchronized (room)`, which assumes a single JVM.
- No rate limiting, idempotency keys, or command acknowledgements.
- Room codes are 6 random alphanumeric characters; collisions are retried on creation.

## Recommended follow-up order

1. **Consistency:** make REST gameplay publish events or be removed, and add CORS/proxy support
   (items 2 and 4).
2. **Resilience:** add a state snapshot on connect/reconnect (item 1).
3. **Security:** validate a player token on every command and bind it to the session (item 3).
4. **Operations:** add room TTL and persistence if deployment continues beyond the MVP (items 5–6).
