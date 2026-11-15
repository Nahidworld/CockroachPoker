package com.cockroach.backend.service;

import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LeaveOutcome;
import com.cockroach.backend.enums.LossReason;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.exception.ServerBusyException;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Leaving rooms, player-name rules, room limits and expiry of idle rooms. */
class LeaveRoomTests {

    private final RoomService roomService = new RoomService(new CardDistributionService(new CardService()));

    // ---------- leaving ----------

    @Test
    void playerLeavingTheLobbyIsRemoved() {
        Room room = createRoom(3);
        Player bob = room.getPlayers().get(1);

        LeaveRoomResponse response = roomService.leaveRoom(room.getCode(), bob.getId());

        assertEquals(LeaveOutcome.LEFT_LOBBY, response.outcome());
        assertEquals("Player 2", response.playerName());
        assertEquals(2, room.getPlayers().size());
        assertFalse(room.getGameState().hasPlayer(bob.getId()));
        assertEquals(GameStatus.WAITING, response.status());
    }

    @Test
    void hostLeavingTheLobbyPassesHostToTheNextPlayer() {
        Room room = createRoom(3);
        Player host = room.getPlayers().get(0);
        Player next = room.getPlayers().get(1);

        LeaveRoomResponse response = roomService.leaveRoom(room.getCode(), host.getId());

        assertEquals(next.getId(), room.getHostPlayerId());
        assertEquals(next.getId(), response.hostPlayerId());
    }

    @Test
    void lastPlayerLeavingTheLobbyClosesTheRoom() {
        Room room = createRoom(1);

        LeaveRoomResponse response = roomService.leaveRoom(room.getCode(), room.getHostPlayerId());

        assertEquals(LeaveOutcome.ROOM_CLOSED, response.outcome());
        assertNull(response.hostPlayerId());
        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom(room.getCode()));
    }

    @Test
    void aClosedRoomCannotBeJoined() {
        Room room = createRoom(1);
        roomService.leaveRoom(room.getCode(), room.getHostPlayerId());

        assertThrows(RoomNotFoundException.class, () -> roomService.joinRoom(room.getCode(), "Late"));
    }

    @Test
    void leavingFreesASeatInAFullRoom() {
        Room room = createRoom(6);
        roomService.leaveRoom(room.getCode(), room.getPlayers().get(5).getId());

        roomService.joinRoom(room.getCode(), "Newcomer");

        assertEquals(6, room.getPlayers().size());
    }

    @Test
    void leavingDuringAGameIsAForfeit() {
        Room room = createRoom(3);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player leaver = room.getPlayers().get(2);

        LeaveRoomResponse response = roomService.leaveRoom(room.getCode(), leaver.getId());

        assertEquals(LeaveOutcome.FORFEITED, response.outcome());
        assertEquals(GameStatus.FINISHED, room.getStatus());
        assertEquals(leaver.getId(), room.getGameState().getLoserPlayerId());
        assertEquals(LossReason.LEFT_GAME, room.getGameState().getLossReason());
        assertEquals(3, room.getPlayers().size(), "players stay listed so the result can be shown");
    }

    @Test
    void leavingWhileACardIsInFlightClearsIt() {
        Room room = createRoom(3);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        roomService.playCard(room.getCode(), sender.getId(), sender.getHand().get(0).getId(),
                receiver.getId(), sender.getHand().get(0).getCreatureType());

        roomService.leaveRoom(room.getCode(), receiver.getId());

        assertFalse(room.getGameState().hasPendingCard());
        assertEquals(receiver.getId(), room.getGameState().getLoserPlayerId());
    }

    @Test
    void leavingAfterTheGameEndedChangesNothing() {
        Room room = createRoom(2);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player first = room.getPlayers().get(0);
        Player second = room.getPlayers().get(1);
        roomService.leaveRoom(room.getCode(), first.getId());

        LeaveRoomResponse response = roomService.leaveRoom(room.getCode(), second.getId());

        assertEquals(LeaveOutcome.ALREADY_FINISHED, response.outcome());
        assertEquals(first.getId(), room.getGameState().getLoserPlayerId());
    }

    @Test
    void nonMemberCannotLeave() {
        Room room = createRoom(2);

        assertThrows(IllegalArgumentException.class, () -> roomService.leaveRoom(room.getCode(), "stranger"));
    }

    @Test
    void ruleBasedLossesRecordTheirReason() {
        Room room = createRoom(2);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        room.getGameState().finishGame(room.getPlayers().get(1).getId(), LossReason.EMPTY_HAND);

        assertEquals(LossReason.EMPTY_HAND,
                roomService.getPlayerView(room.getCode(), room.getPlayers().get(0).getToken()).lossReason());
    }

    @Test
    void playersCannotBeRemovedFromTheModelOnceTheGameStarted() {
        Room room = createRoom(2);
        roomService.startGame(room.getCode(), room.getHostPlayerId());

        assertThrows(IllegalStateException.class,
                () -> room.getGameState().removePlayer(room.getPlayers().get(1).getId()));
    }

    // ---------- player names ----------

    @Test
    void namesAreTrimmed() {
        Room room = roomService.createRoom("  Bob  ");
        Player joined = roomService.joinRoom(room.getCode(), "\tAnn ");

        assertEquals("Bob", room.getPlayers().get(0).getName());
        assertEquals("Ann", joined.getName());
    }

    @Test
    void namesUpToTheLimitAreAccepted() {
        String name = "A".repeat(RoomService.MAX_PLAYER_NAME_LENGTH);

        assertEquals(name, roomService.createRoom(name).getPlayers().get(0).getName());
    }

    @Test
    void namesOverTheLimitAreRejected() {
        String tooLong = "A".repeat(RoomService.MAX_PLAYER_NAME_LENGTH + 1);
        Room room = createRoom(1);

        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom(tooLong));
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), tooLong));
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom("B".repeat(2_000_000)));
    }

    @Test
    void lengthIsCheckedAfterTrimming() {
        String padded = "  " + "A".repeat(RoomService.MAX_PLAYER_NAME_LENGTH) + "  ";

        assertEquals(RoomService.MAX_PLAYER_NAME_LENGTH,
                roomService.createRoom(padded).getPlayers().get(0).getName().length());
    }

    // ---------- room limits and expiry ----------

    @Test
    void creatingMoreThanTheMaximumNumberOfRoomsIsRefused() {
        for (int i = 0; i < RoomService.MAX_ROOMS; i++) {
            roomService.createRoom("P" + i);
        }

        assertThrows(ServerBusyException.class, () -> roomService.createRoom("One too many"));
        assertEquals(RoomService.MAX_ROOMS, roomService.getRoomCount());
    }

    @Test
    void idleRoomsAreRemovedAfterTheIdleLimit() {
        Room room = createRoom(2);
        Instant now = room.getLastActivityAt();

        assertEquals(0, roomService.removeExpiredRooms(now.plus(Duration.ofMinutes(59)),
                Duration.ofMinutes(60), Duration.ofMinutes(10)));
        assertEquals(1, roomService.removeExpiredRooms(now.plus(Duration.ofMinutes(61)),
                Duration.ofMinutes(60), Duration.ofMinutes(10)));
        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom(room.getCode()));
    }

    @Test
    void finishedRoomsUseTheShorterLimit() {
        Room finished = createRoom(2);
        roomService.startGame(finished.getCode(), finished.getHostPlayerId());
        roomService.leaveRoom(finished.getCode(), finished.getPlayers().get(0).getId());
        Room waiting = createRoom(2);
        Instant later = finished.getLastActivityAt().plus(Duration.ofMinutes(11));

        int removed = roomService.removeExpiredRooms(later, Duration.ofMinutes(60), Duration.ofMinutes(10));

        assertEquals(1, removed);
        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom(finished.getCode()));
        assertEquals(waiting, roomService.getRoom(waiting.getCode()));
    }

    @Test
    void activityKeepsARoomAlive() throws InterruptedException {
        Room room = createRoom(1);
        Instant created = room.getLastActivityAt();
        Thread.sleep(5);

        roomService.joinRoom(room.getCode(), "Bob");

        assertTrue(room.getLastActivityAt().isAfter(created));
    }

    @Test
    void cleanupServiceSweepsExpiredRooms() {
        RoomCleanupService cleanup = new RoomCleanupService(roomService, new RoomCreationRateLimiter(10), 60, 10);
        Room room = createRoom(1);

        assertEquals(0, cleanup.sweep(Instant.now()));
        assertEquals(1, cleanup.sweep(room.getLastActivityAt().plus(Duration.ofMinutes(61))));
    }

    private Room createRoom(int playerCount) {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        return room;
    }
}
