# AGENTS.md — Cockroach Poker

## 1. Project Overview

Build a functional real-time multiplayer Cockroach Poker web application within a 12-hour hackathon.

Priorities: correct game rules, reliable multiplayer functionality, simple architecture, and a playable MVP.

## 2. Technology Stack

**Backend**
- Java 21
- Spring Boot 4
- Maven
- Spring WebSocket + STOMP
- Lombok
- JUnit 5
- In-memory state; no database for MVP

**Frontend**
- React
- TypeScript
- Vite

**Architecture**
- Modular monolith
- REST for room management
- WebSocket for real-time gameplay
- Server-authoritative game logic

## 3. Project Structure

Backend base package: `com.cockroach.backend`

```text
com.cockroach.backend/
├── config/
├── controller/
├── service/
├── model/
├── enums/
├── dto/
├── exception/
└── mapper/
```

Follow the existing structure. Do not create unnecessary packages or classes.

## 4. Game Rules

### Players and Cards

- 2–6 players.
- 64 cards total.
- 8 creature types, with 8 cards each.

```text
COCKROACH
STINK_BUG
BAT
FLY
TOAD
RAT
SCORPION
SPIDER
```

### Gameplay

1. A player selects a card from their hand.
2. They pass it face-down to another eligible player.
3. They declare a creature type, truthfully or falsely.
4. The receiver can challenge the declaration or secretly inspect the card and pass it to another eligible player.
5. A player who has already seen the pending card cannot receive it again.
6. The last eligible player must challenge.

### Challenge Resolution

- Correct challenge: the latest claimant takes the card.
- Incorrect challenge: the challenger takes the card.
- The card is added face-up to the recipient's collected cards.
- The player who receives the card starts the next round.

### Losing Conditions

A player loses immediately when:

- They collect four cards of the same creature type.
- They must start a round but have no cards in their hand.

### Critical Game Logic

- Track the original sender separately from the latest claimant.
- Track every player who has seen the pending card.
- Correct challenges penalize the latest claimant, not necessarily the original sender.
- Never allow a player to receive a card they have already seen.
- Validate every action on the backend.

## 5. Card Distribution

Distribute all 64 cards as evenly as possible.

| Players | Distribution |
|---|---|
| 2 | 32, 32 |
| 3 | 22, 21, 21 |
| 4 | 16, 16, 16, 16 |
| 5 | 13, 13, 13, 13, 12 |
| 6 | 11, 11, 11, 11, 10, 10 |

Give remainder cards to the first players in player-list order.

## 6. Backend Rules

- The backend is the single source of truth.
- Never trust frontend-submitted game outcomes.
- Validate room membership, game status, turn ownership, card ownership, and action targets.
- Never expose private hands or internal game-state objects through public APIs.
- Use DTOs for REST and WebSocket communication.
- Keep controllers thin and business logic in services.
- Keep game state in memory.
- Use meaningful exceptions and consistent error handling.
- Maintain encapsulation of sensitive game-state fields.

## 7. Java and Lombok Conventions

Lombok is already installed. Do not add it again.

- Use constructor injection for Spring dependencies.
- Use Lombok where it meaningfully reduces boilerplate.
- Prefer `@Getter`, `@RequiredArgsConstructor`, and appropriate constructors.
- Use `@Builder` when useful.
- Avoid blindly using `@Data` on game-state entities.
- Avoid public setters for sensitive state.
- Use controlled methods to modify hands, collected cards, and game state.
- Follow standard Java naming conventions.

## 8. Code Quality

- Prefer simple, readable, maintainable solutions.
- Keep classes and methods focused.
- Avoid unnecessary abstractions, dependencies, and duplicate logic.
- Follow existing architecture and conventions.
- Do not rewrite working code without a clear reason.
- Do not modify unrelated files.
- Do not silently change game rules.
- Do not invent missing requirements. Identify ambiguity and ask when it affects implementation.

## 9. Testing

Use JUnit 5.

Test normal cases, edge cases, and invalid actions for:

- Deck generation and card distribution.
- Player and room management.
- Game creation and starting.
- Card ownership and turn validation.
- Challenge resolution.
- Peek and pass restrictions.
- Losing conditions.
- Public/private game-state responses.

Rules:
- Add or update tests for every meaningful feature.
- Preserve existing tests.
- Never weaken or delete tests merely to obtain a passing build.
- Run relevant tests after each task.
- Run the complete suite before declaring a task complete.

Command:

```bash
./mvnw test
```

Never claim tests pass unless they were actually executed successfully.

## 10. OpenCode Workflow

For every task:

1. Read this file and `PROJECT.md`.
2. Inspect relevant existing code.
3. Identify required changes and dependencies.
4. Implement only the requested task.
5. Reuse existing working components.
6. Add focused tests.
7. Run relevant tests and fix introduced failures.

Do not:
- Recreate existing classes unnecessarily.
- Implement future tasks prematurely.
- Refactor unrelated code.
- Add dependencies without justification.
- Repeat project documentation in every task.
- Generate lengthy explanations unless requested.

If a prerequisite is missing or an existing implementation conflicts with the requirements, report it before making assumptions.

### Completion Report

Keep the report concise:

- Files created/modified.
- What was implemented.
- Tests executed and actual results.
- Remaining issues or blockers.

## 11. Hackathon Priorities

Implement in this order:

1. Card system.
2. Player and game-state models.
3. Card distribution.
4. Room creation and joining.
5. Starting a game.
6. Playing cards and declaring creatures.
7. Challenge resolution.
8. Peek and pass.
9. Losing conditions.
10. WebSocket synchronization.
11. Frontend integration.
12. Deployment and bug fixing.

Avoid unnecessary features:

- Authentication.
- Database persistence.
- Chat.
- Leaderboards.
- Ranking systems.
- Complex animations.

**Final objective:** Deliver a complete, playable multiplayer MVP with correct game rules.
