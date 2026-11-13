# Cockroach Poker

A multiplayer web-based implementation of the Cockroach Poker card game.

## Tech Stack

- Frontend: 
- Backend: Java Spring Boot
- Communication: WebSocket + STOMP
- Build: Maven
- Storage: In-memory

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
