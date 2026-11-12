package com.cockroach.backend.model;

import com.cockroach.backend.enums.GameStatus;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Getter
public class Room {

    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private final String code;
    private final GameState gameState;
    private final String hostPlayerId;

    public Room(String code, Player host) {
        if (code == null || !ROOM_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Room code must be 6 uppercase alphanumeric characters");
        }
        Objects.requireNonNull(host, "host must not be null");

        this.code = code;
        this.gameState = new GameState();
        this.gameState.addPlayer(host);
        this.hostPlayerId = host.getId();
    }

    public List<Player> getPlayers() {
        return gameState.getPlayers();
    }

    public GameStatus getStatus() {
        return gameState.getStatus();
    }

    public boolean isFull() {
        return gameState.getPlayers().size() >= GameState.MAX_PLAYERS;
    }
}
