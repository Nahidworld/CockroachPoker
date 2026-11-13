# Cockroach Poker — Backend API Documentation

This directory documents the backend contract for the Cockroach Poker MVP.

- **REST** handles room management plus the gameplay actions currently exposed over HTTP.
- **WebSocket + STOMP** handles real-time gameplay commands and room events.
- **The backend is authoritative**: every action is validated server-side and clients never submit game outcomes.

## Base URL

```text
http://localhost:8080
```

WebSocket endpoint:

```text
ws://localhost:8080/ws
```

## Documentation index

| Document | What it covers |
|---|---|
| [rest-api.md](./rest-api.md) | All HTTP endpoints, payloads, status codes, and the `ProblemDetail` error shape |
| [websocket-api.md](./websocket-api.md) | STOMP connection, commands, subscriptions, events, private queues, and WS errors |
| [data-models.md](./data-models.md) | Request/response DTOs, enums, privacy matrix, card distribution, losing conditions |
| [game-flow.md](./game-flow.md) | Rules-to-API mapping, state machine, challenge truth table, and an end-to-end walkthrough |
| [limitations.md](./limitations.md) | Known gaps and caveats, including no state replay and no auth |

Generated interactive reference (REST only):

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

STOMP is not described by OpenAPI, so [websocket-api.md](./websocket-api.md) is the source of truth for real-time behaviour.

## Conventions

| Item | Convention |
|---|---|
| Content type | `application/json` |
| Room code | 6 characters, `A`–`Z` and `0`–`9`; input is trimmed and uppercased |
| Player ID | UUID string, generated when a player is created |
| Player token | Secret UUID string, generated when a player is created; send as `X-Player-Token` |
| Card ID | UUID string, generated when a card is created |
| Creature type | Uppercase enum: `COCKROACH`, `STINK_BUG`, `BAT`, `FLY`, `TOAD`, `RAT`, `SCORPION`, `SPIDER` |
| Game status | `WAITING`, `IN_PROGRESS`, `FINISHED` |
| Success body | The DTO is returned directly (no envelope) |
| REST errors | RFC 9457 `ProblemDetail` for handled exceptions |
| WS null fields | Omitted from the JSON body, not sent as `null` |

## Quick start

1. `POST /api/rooms` with a player name to create a room; keep `roomCode`, `playerId`, and
   `playerToken`.
2. `POST /api/rooms/{roomCode}/join` for each additional player (2–6 players total); each player
   keeps their own `playerId` and `playerToken`.
3. `GET /api/rooms/{roomCode}/me` with `X-Player-Token` to read your hand; `GET /api/rooms/{roomCode}`
   lists every player ID.
4. `POST /api/rooms/{roomCode}/start` as the host.
5. Subscribe to `/topic/rooms/{roomCode}`, `/user/queue/peek`, and `/user/queue/errors` over STOMP.
6. Send gameplay commands to `/app/rooms/{roomCode}/play`, `/challenge`, `/peek`, and `/pass`.

> **Important:** player IDs and hands are now exposed (`/me` returns only the caller's hand). The
> remaining gaps are state replay, REST gameplay events, and authentication — see
> [limitations.md](./limitations.md).

## Player token

Create and join return a secret `playerToken`. Send it in the `X-Player-Token` header to
`GET /api/rooms/{roomCode}/me`. The token is the only thing protecting a hand, so keep it private.
It is not currently required by gameplay commands, which still trust `playerId`.

## No authentication — by design

There is no authentication or authorization. Every WebSocket connection receives an anonymous
per-session principal, which is only an addressing handle for private `/user` queues. Player
identity is whatever `playerId` the client sends in the payload. Do not treat `playerId` as a
security boundary; `playerToken` only guards `/me`.

## State and scaling

- All rooms and games are stored in memory; a restart deletes every room.
- Rooms are never expired or deleted.
- Game actions are serialized per room with `synchronized (room)`.
- The design assumes a single backend instance; there is no shared state or clustering support.
