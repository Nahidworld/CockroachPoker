package com.cockroach.backend.service;

import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LeaveOutcome;
import com.cockroach.backend.enums.LossReason;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class PresenceServiceTests {

    private static final long GRACE_MILLIS = 150;

    private final RoomService roomService = new RoomService(new CardDistributionService(new CardService()));
    private final RoomEventPublisher publisher = mock(RoomEventPublisher.class);
    private final PresenceService presence = new PresenceService(roomService, publisher, GRACE_MILLIS);

    @AfterEach
    void stop() {
        presence.stop();
    }

    @Test
    void disconnectedPlayerForfeitsAfterTheGracePeriod() throws InterruptedException {
        Room room = startedGame(3);
        Player bob = room.getPlayers().get(1);
        presence.connected("s1", room.getCode(), bob.getId());

        presence.disconnected("s1");

        verify(publisher).publishPlayerDisconnected(
                eq(room.getCode()), eq(GameStatus.IN_PROGRESS), eq(bob.getId()), eq("Player 2"), anyLong());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus(), "still in the game during the grace period");

        ArgumentCaptor<LeaveRoomResponse> leave = ArgumentCaptor.forClass(LeaveRoomResponse.class);
        verify(publisher, timeout(2000)).publishPlayerLeft(leave.capture(), any(Room.class));
        assertEquals(LeaveOutcome.FORFEITED, leave.getValue().outcome());
        assertEquals(bob.getId(), room.getGameState().getLoserPlayerId());
        assertEquals(LossReason.LEFT_GAME, room.getGameState().getLossReason());
    }

    @Test
    void reconnectingWithinTheGracePeriodKeepsThePlayerIn() throws InterruptedException {
        Room room = startedGame(3);
        Player bob = room.getPlayers().get(1);
        presence.connected("s1", room.getCode(), bob.getId());
        presence.disconnected("s1");

        presence.connected("s2", room.getCode(), bob.getId());
        Thread.sleep(GRACE_MILLIS * 3);

        verify(publisher).publishPlayerReconnected(room.getCode(), GameStatus.IN_PROGRESS, bob.getId(), "Player 2");
        verify(publisher, never()).publishPlayerLeft(any(), any());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertTrue(presence.isConnected(room.getCode(), bob.getId()));
    }

    @Test
    void aReloadThatConnectsBeforeTheOldSocketClosesIsNotADisconnect() throws InterruptedException {
        Room room = startedGame(2);
        Player bob = room.getPlayers().get(1);
        presence.connected("old", room.getCode(), bob.getId());
        presence.connected("new", room.getCode(), bob.getId());

        presence.disconnected("old");
        Thread.sleep(GRACE_MILLIS * 3);

        verify(publisher, never()).publishPlayerDisconnected(anyString(), any(), anyString(), anyString(), anyLong());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
    }

    @Test
    void disconnectingFromTheLobbyRemovesThePlayer() {
        Room room = roomService.createRoom("Host");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");
        presence.connected("s1", room.getCode(), bob.getId());

        presence.disconnected("s1");

        verify(publisher, timeout(2000)).publishPlayerLeft(any(LeaveRoomResponse.class), any(Room.class));
        assertFalse(room.getGameState().hasPlayer(bob.getId()));
    }

    @Test
    void disconnectAfterTheGameEndedDoesNothing() throws InterruptedException {
        Room room = startedGame(2);
        Player alice = room.getPlayers().get(0);
        Player bob = room.getPlayers().get(1);
        roomService.leaveRoom(room.getCode(), alice.getId());
        presence.connected("s1", room.getCode(), bob.getId());

        presence.disconnected("s1");
        Thread.sleep(GRACE_MILLIS * 3);

        verify(publisher, never()).publishPlayerDisconnected(anyString(), any(), anyString(), anyString(), anyLong());
        verify(publisher, never()).publishPlayerLeft(any(), any());
    }

    @Test
    void unknownSessionsAreIgnored() {
        presence.disconnected("never-connected");

        verify(publisher, never()).publishPlayerDisconnected(anyString(), any(), anyString(), anyString(), anyLong());
    }

    private Room startedGame(int players) {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= players; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }
}
