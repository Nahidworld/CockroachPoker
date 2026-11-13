# Cockroach Poker — Progress Log

Last updated: 2026-11-13

## Objective

Build a real-time multiplayer Cockroach Poker backend incrementally.
Latest completed task: **WebSocket + STOMP gameplay synchronization**.

## Requirements

- Java 21, Spring Boot 4, Maven; in-memory state; no database, no authentication; REST kept for room management.
- Server-authoritative game logic; validate all actions; never expose private hands or pending-card identity.
- WebSocket config: `/ws` endpoint, `/app` app prefix, `/topic` public, `/user/queue` private, in-memory STOMP broker.
- WebSocket commands: `/app/rooms/{roomCode}/play`, `/challenge`, `/peek`, `/pass` using existing DTOs/services; no duplicated game rules.
- Public events on `/topic/rooms/{roomCode}`: player joined, game started, card passed, challenge resolved, card collected, turn changed, game finished. Private peek results only to the requesting player.
- Validate room membership per command; structured errors; no internal exceptions/stack traces; document that client-supplied player IDs are not secure authentication.
- Tests: connection config, valid routing, invalid room/player rejection, public event publishing, private peek delivery, no private card info in public events, failed commands publish no success events.
- Constraints: do not rewrite services, do not add auth/DB, do not modify unrelated frontend files, avoid unnecessary dependencies; run `./mvnw test`.

## Key Decisions

- **No auth (by design):** each WebSocket connection gets an anonymous per-session `Principal` via `DefaultHandshakeHandler.determineUser` (random UUID). Required because Spring's user registry skips sessions with a null Principal, so `/user` sends fail without one.
- Peek result sent via `convertAndSendToUser(principal.getName(), "/queue/peek", ...)`; errors via `/queue/errors`.
- Single `GameEventResponse` envelope DTO with `GameEventType` enum and `@JsonInclude(NON_NULL)`; `creatureType` only populated after a challenge reveals the card (in-flight pending card never exposed).
- Publish "player joined" / "game started" from the REST `RoomController` (WS has no join/start mappings); other events from `RoomWebSocketController`.
- Publishing happens in controllers after successful service calls, so existing `RoomService` constructor/tests stay unchanged.
- Membership validation added in the WS controller (`requireMember`) using `roomService.getRoom` + `GameState.hasPlayer`; all other validation delegated to `RoomService`.
- `RoomEventPublisher` placed in the `service` package (per existing package structure).

## Work State

### Completed

- Prior gameplay work committed and pushed (5 feature commits ending `79495bf`).
- WebSocket config, WS controller, event DTOs/enum, `RoomEventPublisher`, REST controller join/start publishing implemented.
- `RoomEventPublisherTests` (5 tests) and `RoomWebSocketTests` (7 real STOMP integration tests) pass.
- Added two event-publishing tests to `RoomControllerTests` (`joiningRoomPublishesPlayerJoinedEvent`, `startingGamePublishesGameStartedEvent`).
- Full suite run: `./mvnw test` → **149 run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.**
- All WebSocket work committed as `46a2e37` and pushed to `origin/main`.

### Active

- (none)

### Blocked

- (none)

## Next Move

- (none — task complete; await next instruction, likely frontend integration)

## Events Reference

Public events on `/topic/rooms/{roomCode}`:
`PLAYER_JOINED`, `GAME_STARTED`, `CARD_PASSED`, `CHALLENGE_RESOLVED`, `CARD_COLLECTED`, `TURN_CHANGED`, `GAME_FINISHED`.

Private events (via `/user` queue):
- `/queue/peek` — peek results, only to the requesting player.
- `/queue/errors` — structured errors.

No hands, pending-card identity, or internal state leaked; `creatureType` is exposed only after a challenge reveals the card.

## Relevant Files

- `backend/src/main/java/com/cockroach/backend/config/WebSocketConfig.java` — broker/endpoint config and anonymous principal handshake handler.
- `backend/src/main/java/com/cockroach/backend/controller/RoomWebSocketController.java` — STOMP gameplay commands, membership validation, error mapping.
- `backend/src/main/java/com/cockroach/backend/service/RoomEventPublisher.java` — event destinations and publish helpers.
- `backend/src/main/java/com/cockroach/backend/dto/GameEventResponse.java` — public event envelope with factories.
- `backend/src/main/java/com/cockroach/backend/enums/GameEventType.java` — event type enum.
- `backend/src/main/java/com/cockroach/backend/dto/ErrorResponse.java` — structured WS error DTO.
- `backend/src/main/java/com/cockroach/backend/controller/RoomController.java` — REST endpoints publish join/start events.
- `backend/src/test/java/com/cockroach/backend/controller/RoomWebSocketTests.java` — STOMP integration tests.
- `backend/src/test/java/com/cockroach/backend/service/RoomEventPublisherTests.java` — publisher unit tests (Mockito).
- `backend/src/test/java/com/cockroach/backend/controller/RoomControllerTests.java` — standalone MockMvc tests incl. event-publishing tests.
- `backend/src/main/java/com/cockroach/backend/service/RoomService.java` — existing gameplay business logic (unchanged).
- `backend/src/main/java/com/cockroach/backend/model/GameState.java` — state transitions and losing conditions (unchanged).

## Important Context

- Working tree clean; local `main` in sync with `origin/main` at `46a2e37`.
- `spring-boot-starter-websocket` and `spring-boot-starter-websocket-test` already in `pom.xml`; no new dependencies added.
- Spring 7 / Jackson 3: message converter is `JacksonJsonMessageConverter`; `StompHeaders.setContentType(MimeType)`.
- `RoomEventPublisher` destinations: room topic `/topic/rooms/{roomCode}`; private `/queue/peek` and `/queue/errors` via `convertAndSendToUser`.
- Test helper pattern: `new RoomService(new CardDistributionService(new CardService()))`; `RoomControllerTests` mocks `RoomEventPublisher`.
- Gotcha: after `playCard`, the played card leaves the hand, so tests must read the creature from the pending card, not `hand.get(0)`; incorrect challenge (truthful declaration + `guess=false`) gives the card to the challenger.
- WS integration tests use real sockets with ~5s timeouts and are the slowest suite part.

## Git History (recent)

```
46a2e37 Add WebSocket + STOMP real-time gameplay synchronization
79495bf Centralize losing conditions and detect empty hand
bd352a3 Implement secret peek and pass
ea4e948 Implement challenge resolution
66c6b33 Implement playing a card with a declared creature
3c171f1 Implement game start endpoint
2eef728 Room Creation and Player joining-RoomController
54c32c9 CardDistributionService update
61318d9 Game Start and Status Updated
5083578 test backend
e67f681 structure
```
