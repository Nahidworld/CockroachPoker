# Cockroach Poker

A multiplayer web-based implementation of the Cockroach Poker card game.

## Tech Stack

- Frontend: 
- Backend: Java Spring Boot
- Communication: WebSocket + STOMP
- Build: Maven
- Storage: In-memory (plus an in-memory H2 database for the dev console)

## Requirements
- 2-6 players
- 64 cards
- 8 creature types
- Bluffing and card passing
- Peek and Pass
- Call Bluff
- Four-of-a-kind losing condition
- Empty-hand losing condition

## Backend

```bash
cd backend
./mvnw spring-boot:run
```

The API is served at `http://localhost:8080` and the WebSocket endpoint is `ws://localhost:8080/ws`.

Run the backend test suite with:

```bash
cd backend
./mvnw test
```

## H2 console

The backend also starts an in-memory H2 database and serves its web console:

- Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:cockroachpoker`
- User: `sa`
- Password: *(empty)*

The game does **not** use this database: rooms, players, hands, and games are still held in memory,
so the console starts with an empty `PUBLIC` schema and loses everything on restart. It exists for
local inspection and for adding tables later. There is no Spring Security in front of it, so keep it
disabled or unreachable in any deployed environment (`spring.h2.console.enabled=false`).

## API documentation

- [API overview](docs/api/README.md)
- [REST endpoints](docs/api/rest-api.md)
- [WebSocket / STOMP contract](docs/api/websocket-api.md)
- [Data models](docs/api/data-models.md)
- [Game flow](docs/api/game-flow.md)
- [Known limitations](docs/api/limitations.md)

Generated REST reference:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

STOMP/WebSocket behaviour is not covered by OpenAPI; use the WebSocket document above.
