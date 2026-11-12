package com.cockroach.backend.model;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import lombok.AccessLevel;
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
    public static final int LOSING_CREATURE_COUNT = 4;

    private final String id;
    private final List<Player> players = new ArrayList<>();
    private GameStatus status = GameStatus.WAITING;
    private String currentPlayerId;
    private String loserPlayerId;

    @Getter(AccessLevel.NONE)
    private PendingCard pendingCard;

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

    public Optional<PendingCard> getPendingCard() {
        return Optional.ofNullable(pendingCard);
    }

    public boolean hasPendingCard() {
        return pendingCard != null;
    }

    public void setPendingCard(PendingCard pendingCard) {
        Objects.requireNonNull(pendingCard, "pendingCard must not be null");
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("A pending card can only exist while the game is in progress");
        }
        if (this.pendingCard != null) {
            throw new IllegalStateException("A pending card already exists");
        }
        this.pendingCard = pendingCard;
    }

    public void clearPendingCard() {
        if (pendingCard == null) {
            throw new IllegalStateException("No pending card to clear");
        }
        pendingCard = null;
    }

    public void updateCurrentPlayer(String playerId) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Current player can only be changed while the game is in progress");
        }
        currentPlayerId = requirePlayer(playerId).getId();
    }

    /**
     * Starts the next round with the given player, applying both losing conditions in one place.
     * A player who has collected four cards of the same creature type or who has no cards left
     * in hand loses immediately and the game is finished.
     */
    public void startNextRound(String startingPlayerId) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Next round can only be started while the game is in progress");
        }
        Player startingPlayer = requirePlayer(startingPlayerId);
        if (hasCollectedLosingCreatureCount(startingPlayer) || startingPlayer.getHand().isEmpty()) {
            finishGame(startingPlayer.getId());
            return;
        }
        currentPlayerId = startingPlayer.getId();
    }

    private boolean hasCollectedLosingCreatureCount(Player player) {
        for (CreatureType creatureType : CreatureType.values()) {
            if (player.countCollectedCards(creatureType) >= LOSING_CREATURE_COUNT) {
                return true;
            }
        }
        return false;
    }

    public void finishGame(String loserPlayerId) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress game can be finished");
        }
        requirePlayer(loserPlayerId);
        this.loserPlayerId = loserPlayerId;
        this.pendingCard = null;
        this.currentPlayerId = null;
        this.status = GameStatus.FINISHED;
    }
}
