package com.cockroach.backend.service;

import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.RoomFullException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTests {

    private final RoomService roomService = new RoomService();

    @Test
    void createRoomCreatesWaitingRoomWithCreatorAsHost() {
        Room room = roomService.createRoom("Alice");

        assertTrue(room.getCode().matches("[A-Z0-9]{6}"));
        assertEquals(GameStatus.WAITING, room.getStatus());
        assertEquals(1, room.getPlayers().size());

        Player host = room.getPlayers().get(0);
        assertEquals("Alice", host.getName());
        assertEquals(host.getId(), room.getHostPlayerId());
        assertSame(room, roomService.getRoom(room.getCode()));
    }

    @Test
    void roomCodesAreUnique() {
        Set<String> roomCodes = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            roomCodes.add(roomService.createRoom("Player " + i).getCode());
        }

        assertEquals(200, roomCodes.size());
    }

    @Test
    void joinRoomAddsPlayerToWaitingRoom() {
        Room room = roomService.createRoom("Alice");

        Room joinedRoom = roomService.joinRoom(room.getCode(), "Bob");

        assertSame(room, joinedRoom);
        assertEquals(2, joinedRoom.getPlayers().size());
        assertEquals(List.of("Alice", "Bob"), joinedRoom.getPlayers().stream()
                .map(Player::getName)
                .toList());
    }

    @Test
    void getRoomRejectsNonexistentRoom() {
        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom("ZZZZZZ"));
    }

    @Test
    void joinRoomRejectsNonexistentRoom() {
        assertThrows(RoomNotFoundException.class, () -> roomService.joinRoom("ZZZZZZ", "Alice"));
    }

    @Test
    void joinRoomRejectsFullRoom() {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= 6; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }

        assertTrue(room.isFull());
        assertThrows(RoomFullException.class, () -> roomService.joinRoom(room.getCode(), "Player 7"));
        assertEquals(6, room.getPlayers().size());
    }

    @Test
    void joinRoomRejectsStartedRoom() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");
        room.getGameState().startGame(room.getHostPlayerId());

        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertThrows(GameAlreadyStartedException.class,
                () -> roomService.joinRoom(room.getCode(), "Carol"));
        assertEquals(2, room.getPlayers().size());
    }

    @Test
    void createRoomRejectsBlankPlayerNames() {
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom(null));
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom(""));
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom("   "));
    }

    @Test
    void joinRoomRejectsBlankPlayerNames() {
        Room room = roomService.createRoom("Alice");

        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), null));
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), ""));
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), "   "));
    }
}
