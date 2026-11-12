# Cockroach Poker — Project Context

## Project Goal

Build a playable real-time multiplayer Cockroach Poker web application during a hackathon.

## Technology

- Frontend: React + TypeScript + Vite
- Backend: Java 21 + Spring Boot 4 + Maven
- Real-time communication: Spring WebSocket + STOMP
- Styling: To be decided based on existing frontend setup
- State management: In-memory
- Testing: JUnit 5
- Lombok: Already installed in backend

## Architecture

- Modular monolith.
- Backend controls all game rules and state.
- REST API handles room creation and joining.
- WebSocket handles real-time gameplay.
- No database or authentication for MVP.

## Game Requirements

- 2–6 players.
- 64 cards, 8 creature types, 8 cards per type.
- Bluffing and creature declaration.
- Challenge true/false.
- Secretly inspect and pass cards.
- Prevent players from receiving cards they have already seen.
- Four cards of the same creature means losing.
- Starting a round without cards means losing.

## Backend Package

com.cockroach.backend

## Current Progress

### Completed

- Spring Boot project initialized.
- Java 21 configured.
- Card model implemented.
- CreatureType enum implemented.
- CardService implemented.
- 64-card deck generation.
- Three CardService unit tests.
- All four current Maven tests pass.

### In Progress

- Player model.
- GameState model.

### Pending

- Card distribution.
- Room management.
- Game engine.
- Challenge resolution.
- Peek and pass.
- Losing conditions.
- WebSocket integration.
- Frontend integration.
- Deployment.

## Development Rules

- Read AGENTS.md before implementation.
- Inspect existing code before modifying it.
- Implement one task at a time.
- Preserve working functionality.
- Run tests after each task.
- Avoid unnecessary dependencies and token usage.
- Keep implementation simple enough for a hackathon.
