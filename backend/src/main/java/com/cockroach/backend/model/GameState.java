package com.cockroach.backend.model;

import com.cockroach.backend.enums.GameStatus;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Getter
public class GameState {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;

    private final String id;
    private final List<Player> players = new ArrayList<>();
    private GameStatus status = GameStatus.WAITING;
    private String currentPlayerId;

    public GameState() {
        this(UUID.randomUUID().toString());
    }

    public GameState(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Game id must not be blank");
        }
        this.id = id;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        if (status != GameStatus.WAITING) {
            throw new IllegalStateException("Players cannot be added after the game has started");
        }
        if (players.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("A game cannot have more than " + MAX_PLAYERS + " players");
        }
        if (hasPlayer(player.getId())) {
            throw new IllegalArgumentException("Player already added: " + player.getId());
        }
        players.add(player);
    }

    public boolean hasPlayer(String playerId) {
        return findPlayer(playerId).isPresent();
    }

    public Optional<Player> findPlayer(String playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return players.stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst();
    }

    public Player requirePlayer(String playerId) {
        return findPlayer(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
    }

    public void startGame(String startingPlayerId) {
        if (status != GameStatus.WAITING) {
            throw new IllegalStateException("Game has already started");
        }
        if (players.size() < MIN_PLAYERS) {
            throw new IllegalStateException("A game requires at least " + MIN_PLAYERS + " players");
        }
        Player startingPlayer = requirePlayer(startingPlayerId);
        currentPlayerId = startingPlayer.getId();
        status = GameStatus.IN_PROGRESS;
    }
}
