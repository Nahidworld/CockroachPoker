package com.cockroach.backend.controller;

import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.enums.LeaveOutcome;
import com.cockroach.backend.exception.GlobalExceptionHandler;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.CardDistributionService;
import com.cockroach.backend.service.CardService;
import com.cockroach.backend.service.RoomCreationRateLimiter;
import com.cockroach.backend.service.RoomEventPublisher;
import com.cockroach.backend.service.RoomService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST: leaving a room, name limits and the room-creation rate limit. */
class RoomLeaveControllerTests {

    private final RoomService roomService = new RoomService(new CardDistributionService(new CardService()));
    private final RoomEventPublisher publisher = mock(RoomEventPublisher.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RoomController(roomService, publisher, new RoomCreationRateLimiter(3)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void leavingTheLobbyRemovesThePlayerAndTellsTheRoom() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");

        leave(room.getCode(), bob.getId(), bob.getToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("LEFT_LOBBY"))
                .andExpect(jsonPath("$.playerName").value("Bob"));

        assertFalse(room.getGameState().hasPlayer(bob.getId()));
        ArgumentCaptor<LeaveRoomResponse> captor = ArgumentCaptor.forClass(LeaveRoomResponse.class);
        verify(publisher).publishPlayerLeft(captor.capture(), any(Room.class));
        assertEquals(LeaveOutcome.LEFT_LOBBY, captor.getValue().outcome());
    }

    @Test
    void leavingDuringAGameForfeits() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");
        roomService.startGame(room.getCode(), room.getHostPlayerId());

        leave(room.getCode(), bob.getId(), bob.getToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FORFEITED"))
                .andExpect(jsonPath("$.status").value("FINISHED"));

        assertEquals(bob.getId(), room.getGameState().getLoserPlayerId());
    }

    @Test
    void lastPlayerLeavingClosesTheRoomWithoutAnEvent() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player alice = room.getPlayers().get(0);

        leave(room.getCode(), alice.getId(), alice.getToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ROOM_CLOSED"));

        verify(publisher, never()).publishPlayerLeft(any(), any());
        mockMvc.perform(post("/api/rooms/{code}/join", room.getCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Late\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotMakeSomeoneElseLeave() throws Exception {
        Room room = roomService.createRoom("Alice");
        Player bob = roomService.joinRoom(room.getCode(), "Bob");
        Player alice = room.getPlayers().get(0);

        leave(room.getCode(), bob.getId(), alice.getToken()).andExpect(status().isForbidden());
        leave(room.getCode(), bob.getId(), null).andExpect(status().isBadRequest());

        assertEquals(2, room.getPlayers().size());
    }

    @Test
    void nameOverTheLimitIsRejected() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"" + "A".repeat(10_000) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Player name must be at most 16 characters"));
    }

    @Test
    void roomCreationIsRateLimitedPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            createRoom("10.0.0.1").andExpect(status().isCreated());
        }
        createRoom("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many requests"));
        createRoom("10.0.0.2").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions createRoom(String ip) throws Exception {
        return mockMvc.perform(post("/api/rooms")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"P\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions leave(
            String roomCode, String playerId, String token) throws Exception {
        var request = post("/api/rooms/{code}/leave", roomCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}");
        if (token != null) {
            request.header(RoomController.PLAYER_TOKEN_HEADER, token);
        }
        return mockMvc.perform(request);
    }
}
