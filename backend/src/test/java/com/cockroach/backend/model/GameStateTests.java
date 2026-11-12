package com.cockroach.backend.model;

import com.cockroach.backend.enums.GameStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateTests {

    @Test
    void newGameStartsWaitingWithNoPlayers() {
        GameState gameState = new GameState();

        assertEquals(GameStatus.WAITING, gameState.getStatus());
        assertTrue(gameState.getPlayers().isEmpty());
        assertNull(gameState.getCurrentPlayerId());
    }

    @Test
    void addPlayerAndFindById() {
        GameState gameState = new GameState();
        Player player = new Player("Alice");

        gameState.addPlayer(player);

        assertTrue(gameState.hasPlayer(player.getId()));
        assertEquals(player, gameState.requirePlayer(player.getId()));
    }

    @Test
    void addPlayerRejectsDuplicateId() {
        GameState gameState = new GameState();
        Player player = new Player("player-1", "Alice");
        gameState.addPlayer(player);

        Player duplicate = new Player("player-1", "Alice Again");

        assertThrows(IllegalArgumentException.class, () -> gameState.addPlayer(duplicate));
    }

    @Test
    void addPlayerRejectsMoreThanSixPlayers() {
        GameState gameState = new GameState();

        for (int i = 0; i < GameState.MAX_PLAYERS; i++) {
            gameState.addPlayer(new Player("Player " + i));
        }

        assertThrows(IllegalStateException.class, () -> gameState.addPlayer(new Player("Extra")));
    }

    @Test
    void startGameRequiresMinimumPlayers() {
        GameState gameState = new GameState();
        Player player = new Player("Alice");
        gameState.addPlayer(player);

        assertThrows(IllegalStateException.class, () -> gameState.startGame(player.getId()));
    }

    @Test
    void startGameRejectsUnknownStartingPlayer() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player("Alice"));
        gameState.addPlayer(new Player("Bob"));

        assertThrows(IllegalArgumentException.class, () -> gameState.startGame("missing"));
    }

    @Test
    void startGameSetsStatusAndCurrentPlayer() {
        GameState gameState = new GameState();
        Player alice = new Player("Alice");
        Player bob = new Player("Bob");
        gameState.addPlayer(alice);
        gameState.addPlayer(bob);

        gameState.startGame(bob.getId());

        assertEquals(GameStatus.IN_PROGRESS, gameState.getStatus());
        assertEquals(bob.getId(), gameState.getCurrentPlayerId());
        assertThrows(IllegalStateException.class, () -> gameState.startGame(alice.getId()));
    }

    @Test
    void playersCannotBeAddedAfterGameStarts() {
        GameState gameState = new GameState();
        Player alice = new Player("Alice");
        Player bob = new Player("Bob");
        gameState.addPlayer(alice);
        gameState.addPlayer(bob);
        gameState.startGame(alice.getId());

        assertThrows(IllegalStateException.class, () -> gameState.addPlayer(new Player("Carol")));
    }

    @Test
    void playersAreReadOnly() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player("Alice"));

        assertThrows(UnsupportedOperationException.class, () -> gameState.getPlayers().clear());
        assertFalse(gameState.getPlayers().isEmpty());
    }
}
