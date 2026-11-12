package com.cockroach.backend.service;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.RoomFullException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private static final int ROOM_CODE_LENGTH = 6;
    private static final char[] ROOM_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String playerName) {
        validatePlayerName(playerName);

        while (true) {
            String roomCode = generateRoomCode();
            Room room = new Room(roomCode, new Player(playerName));

            if (rooms.putIfAbsent(roomCode, room) == null) {
                return room;
            }
        }
    }

    public Room joinRoom(String roomCode, String playerName) {
        validatePlayerName(playerName);
        Room room = getRoom(roomCode);

        synchronized (room) {
            if (room.getStatus() != GameStatus.WAITING) {
                throw new GameAlreadyStartedException(room.getCode());
            }
            if (room.isFull()) {
                throw new RoomFullException(room.getCode());
            }
            room.getGameState().addPlayer(new Player(playerName));
        }

        return room;
    }

    public Room getRoom(String roomCode) {
        String normalizedCode = normalizeRoomCode(roomCode);
        Room room = rooms.get(normalizedCode);
        if (room == null) {
            throw new RoomNotFoundException(normalizedCode);
        }
        return room;
    }

    private void validatePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
    }

    private String normalizeRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new IllegalArgumentException("Room code must not be blank");
        }
        return roomCode.trim().toUpperCase(Locale.ROOT);
    }

    private String generateRoomCode() {
        StringBuilder roomCode = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            roomCode.append(ROOM_CODE_CHARACTERS[RANDOM.nextInt(ROOM_CODE_CHARACTERS.length)]);
        }
        return roomCode.toString();
    }
}
