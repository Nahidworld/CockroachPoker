# AGENTS.md — Cockroach Poker

## 1. Project Overview

We are building a multiplayer web-based Cockroach Poker game as a 12-hour hackathon project.

The goal is to deliver a functional, real-time multiplayer game with clean architecture and reliable game rules.

## 2. Technology Stack

### Backend

- Java 21
- Spring Boot 4
- Maven
- Spring WebSocket with STOMP
- Lombok
- JUnit 5
- In-memory game state (no database for MVP)

### Frontend

- React
- TypeScript
- Vite

### Architecture

- Modular monolith
- REST API for room management
- WebSocket for real-time gameplay
- Server-authoritative game logic

## 3. Project Structure

Backend base package:

`com.cockroach.backend`

Follow this package structure:

```text
com.cockroach.backend/
├── BackendApplication.java
├── config/
├── controller/
├── service/
├── model/
├── enums/
├── dto/
├── exception/
└── mapper/
```

Do not create unnecessary packages or classes.

## 4. Game Rules

### Players and Cards

- Support 2–6 players.
- The deck contains 64 cards.
- There are 8 creature types.
- Each creature has exactly 8 cards.

Creature types:

- COCKROACH
- STINK_BUG
- BAT
- FLY
- TOAD
- RAT
- SCORPION
- SPIDER

### Gameplay

1. A player selects a card from their hand.
2. The player passes the card face-down to another player.
3. The sender declares a creature type. The declaration can be true or false.
4. The receiving player can:
   - Challenge the declaration by guessing True or False.
   - Secretly inspect the card and pass it to another eligible player.
5. A player who has already seen the card cannot receive it again.
6. The last eligible player cannot pass and must challenge.

### Challenge Resolution

- If the challenger guesses correctly, the latest claimant takes the card.
- If the challenger guesses incorrectly, the challenger takes the card.
- The player who takes the card receives it face-up in their collected cards.
- The player receiving the card starts the next round.

### Losing Conditions

A player loses immediately if:

- They collect four cards of the same creature type.
- They must start a round but have no cards remaining in their hand.

### Important Game Logic

- Track the original sender separately from the latest claimant.
- Track all players who have seen the pending card.
- The latest claimant is responsible for taking the card when a challenge is correct.
- Never allow a player to receive a card they have already seen.
- Validate all game actions on the backend.

## 5. Card Distribution

Distribute cards as evenly as possible.

| Players | Distribution           |
| ------- | ---------------------- |
| 2       | 32, 32                 |
| 3       | 22, 21, 21             |
| 4       | 16, 16, 16, 16         |
| 5       | 12, 12, 12, 12, 12     |
| 6       | 10, 10, 10, 10, 10, 10 |

## 6. Backend Development Rules

- The backend is the single source of truth.
- Never trust frontend-submitted game outcomes.
- Never expose players' private hands to other players.
- Validate room membership before accepting game actions.
- Validate whose turn it is before processing actions.
- Validate card ownership before playing a card.
- Validate targets and game status.
- Keep game state in memory for the MVP.
- Use clear exception handling.
- Keep business logic inside service classes.
- Keep controllers thin.
- Use DTOs for API and WebSocket communication.
- Avoid exposing internal model objects directly.

## 7. Lombok Conventions

Lombok is already included in the project.

- Use Lombok to reduce unnecessary boilerplate.
- Prefer `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@RequiredArgsConstructor` where appropriate.
- Use `@Builder` when it improves object construction.
- Prefer constructor injection for Spring dependencies.
- Do not add Lombok again to `pom.xml`.
- Avoid blindly using `@Data` on game-state entities.
- Avoid public setters for sensitive game-state fields.
- Use controlled methods to modify player hands, collected cards, and game state.
- Maintain encapsulation.

## 8. Code Quality

- Follow Java naming conventions.
- Use meaningful class, method, and variable names.
- Keep methods small and focused.
- Avoid unnecessary abstractions.
- Avoid duplicate code.
- Avoid unnecessary dependencies.
- Do not rewrite working code without a clear reason.
- Do not modify unrelated files.
- Follow the existing architecture.
- Write readable, maintainable code.

## 9. Testing Requirements

Use JUnit 5.

Write focused tests for:

- Deck generation and card distribution.
- Player management.
- Game creation and room management.
- Card ownership.
- Turn validation.
- Challenge resolution.
- Peek and pass restrictions.
- Losing conditions.
- Private and public game-state responses.

Run tests after each meaningful implementation.

Use:

`./mvnw test`

Do not claim tests pass unless they have actually been executed successfully.

## 10. OpenCode Working Instructions

Before implementing any task:

1. Inspect the existing project structure.
2. Read relevant existing classes.
3. Understand current implementation and dependencies.
4. Identify which files need modification.
5. Implement only the requested task.

While coding:

- Work on one focused task at a time.
- Do not regenerate existing working classes unnecessarily.
- Do not implement unrelated features.
- Do not add dependencies without justification.
- Avoid modifying files owned by other developers unless necessary.
- Keep changes small and easy to review.

After implementation:

1. Run relevant tests.
2. Fix compilation errors and failing tests.
3. Summarize changed files.
4. Explain important implementation decisions briefly.
5. Report actual test results.
6. Mention any remaining issues.

## 11. Hackathon Priorities

Prioritize features in this order:

1. Card system.
2. Player and game-state models.
3. Card distribution.
4. Room creation and joining.
5. Starting a game.
6. Playing cards and declaring creatures.
7. Challenge resolution.
8. Peek and pass.
9. Losing conditions.
10. WebSocket real-time synchronization.
11. Frontend integration.
12. Deployment and bug fixing.

Avoid implementing unnecessary features such as:

- User authentication.
- Database persistence.
- Chat system.
- Leaderboards.
- Ranking systems.
- Complex animations.

The priority is a complete, playable multiplayer MVP.
