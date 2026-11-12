package com.cockroach.backend.controller;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.exception.GlobalExceptionHandler;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.CardDistributionService;
import com.cockroach.backend.service.CardService;
import com.cockroach.backend.service.RoomService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomControllerTests {

    private final RoomService roomService =
            new RoomService(new CardDistributionService(new CardService()));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RoomController(roomService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void createRoomReturnsPublicRoomInformation() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomCode", matchesPattern("[A-Z0-9]{6}")))
                .andExpect(jsonPath("$.playerNames", contains("Alice")))
                .andExpect(jsonPath("$.playerCount").value(1))
                .andExpect(jsonPath("$.maxPlayers").value(6))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.*", hasSize(5)));
    }

    @Test
    void joinRoomAddsPlayerAndReturnsUpdatedRoom() throws Exception {
        String roomCode = createRoom("Alice");

        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerNames", contains("Alice", "Bob")))
                .andExpect(jsonPath("$.playerCount").value(2));
    }

    @Test
    void getRoomReturnsPublicRoomInformation() throws Exception {
        String roomCode = createRoom("Alice");

        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.playerNames", contains("Alice")))
                .andExpect(jsonPath("$.playerCount").value(1))
                .andExpect(jsonPath("$.maxPlayers").value(6))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void getRoomReturnsNotFoundForUnknownRoom() throws Exception {
        mockMvc.perform(get("/api/rooms/ZZZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinRoomReturnsNotFoundForUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/ZZZZZZ/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Alice\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinRoomReturnsConflictWhenRoomIsFull() throws Exception {
        String roomCode = createRoom("Player 1");
        for (int i = 2; i <= 6; i++) {
            joinRoom(roomCode, "Player " + i);
        }

        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Player 7\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void joinRoomReturnsConflictWhenGameHasStarted() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");

        Room room = roomService.getRoom(roomCode);
        room.getGameState().startGame(room.getHostPlayerId());

        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"Carol\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createRoomReturnsBadRequestForBlankPlayerName() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinRoomReturnsBadRequestForBlankPlayerName() throws Exception {
        String roomCode = createRoom("Alice");

        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startGameByHostReturnsPublicGameInformation() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        String hostPlayerId = roomService.getRoom(roomCode).getHostPlayerId();

        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + hostPlayerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.playerNames", contains("Alice", "Bob")))
                .andExpect(jsonPath("$.currentPlayerId").value(hostPlayerId))
                .andExpect(jsonPath("$.playerCount").value(2))
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.gameState").doesNotExist())
                .andExpect(jsonPath("$.players").doesNotExist())
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.collectedCards").doesNotExist());
    }

    @Test
    void startGameReturnsForbiddenForNonHost() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        String nonHostPlayerId = roomService.getRoom(roomCode).getPlayers().get(1).getId();

        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + nonHostPlayerId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void startGameReturnsConflictForTooFewPlayers() throws Exception {
        String roomCode = createRoom("Alice");
        String hostPlayerId = roomService.getRoom(roomCode).getHostPlayerId();

        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + hostPlayerId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void startGameReturnsConflictWhenAlreadyStarted() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        String hostPlayerId = roomService.getRoom(roomCode).getHostPlayerId();
        startGame(roomCode, hostPlayerId);

        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + hostPlayerId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void startGameReturnsNotFoundForUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/ZZZZZZ/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"player-id\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void startGameReturnsBadRequestForBlankPlayerId() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");

        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicResponseDoesNotExposePrivateGameData() throws Exception {
        String roomCode = createRoom("Alice");

        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.hostPlayerId").doesNotExist())
                .andExpect(jsonPath("$.gameState").doesNotExist())
                .andExpect(jsonPath("$.players").doesNotExist())
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.collectedCards").doesNotExist());
    }

    @Test
    void playCardReturnsPublicPendingCardInformationWithoutPrivateCardData() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player sender = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);
        String cardId = sender.getHand().get(0).getId();

        mockMvc.perform(post("/api/rooms/{roomCode}/play", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + sender.getId()
                                + "\",\"cardId\":\"" + cardId
                                + "\",\"targetPlayerId\":\"" + target.getId()
                                + "\",\"declaredCreature\":\"BAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.originalSenderId").value(sender.getId()))
                .andExpect(jsonPath("$.latestClaimantId").value(sender.getId()))
                .andExpect(jsonPath("$.declaredCreature").value("BAT"))
                .andExpect(jsonPath("$.seenPlayerIds", contains(sender.getId())))
                .andExpect(jsonPath("$.currentReceiverId").value(target.getId()))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.cardId").doesNotExist())
                .andExpect(jsonPath("$.card").doesNotExist())
                .andExpect(jsonPath("$.actualCreature").doesNotExist())
                .andExpect(jsonPath("$.creatureType").doesNotExist());
    }

    @Test
    void playCardReturnsBadRequestForCardNotOwnedBySender() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player sender = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);
        String cardOwnedByTarget = target.getHand().get(0).getId();

        mockMvc.perform(post("/api/rooms/{roomCode}/play", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + sender.getId()
                                + "\",\"cardId\":\"" + cardOwnedByTarget
                                + "\",\"targetPlayerId\":\"" + target.getId()
                                + "\",\"declaredCreature\":\"BAT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void playCardReturnsConflictForWrongPlayerTurn() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player sender = room.getPlayers().get(0);
        Player nonCurrentPlayer = room.getPlayers().get(1);
        String cardId = nonCurrentPlayer.getHand().get(0).getId();

        mockMvc.perform(post("/api/rooms/{roomCode}/play", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + nonCurrentPlayer.getId()
                                + "\",\"cardId\":\"" + cardId
                                + "\",\"targetPlayerId\":\"" + sender.getId()
                                + "\",\"declaredCreature\":\"BAT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void challengeResolvesCorrectGuess() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player claimant = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        var card = claimant.getHand().get(0);
        playCard(roomCode, claimant, receiver, card.getCreatureType());

        mockMvc.perform(post("/api/rooms/{roomCode}/challenge", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + receiver.getId() + "\",\"guess\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.challengerId").value(receiver.getId()))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.recipientId").value(claimant.getId()))
                .andExpect(jsonPath("$.declaredCreature").value(card.getCreatureType().name()))
                .andExpect(jsonPath("$.actualCreature").value(card.getCreatureType().name()))
                .andExpect(jsonPath("$.currentPlayerId").value(claimant.getId()))
                .andExpect(jsonPath("$.loserPlayerId").value(nullValue()))
                .andExpect(jsonPath("$.*", hasSize(9)));
    }

    @Test
    void challengeReturnsBadRequestForMissingGuess() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player claimant = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(roomCode, claimant, receiver, CreatureType.BAT);

        mockMvc.perform(post("/api/rooms/{roomCode}/challenge", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + receiver.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void challengeReturnsConflictForPlayerOtherThanReceiver() throws Exception {
        String roomCode = createRoom("Alice");
        joinRoom(roomCode, "Bob");
        Room room = roomService.getRoom(roomCode);
        startGame(roomCode, room.getHostPlayerId());

        Player claimant = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(roomCode, claimant, receiver, CreatureType.BAT);

        mockMvc.perform(post("/api/rooms/{roomCode}/challenge", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + claimant.getId() + "\",\"guess\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void challengeReturnsNotFoundForUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/ZZZZZZ/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"player-id\",\"guess\":true}"))
                .andExpect(status().isNotFound());
    }

    private String createRoom(String playerName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"" + playerName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.roomCode");
    }

    private void joinRoom(String roomCode, String playerName) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"" + playerName + "\"}"))
                .andExpect(status().isOk());
    }

    private void startGame(String roomCode, String playerId) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomCode}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isOk());
    }

    private void playCard(
            String roomCode, Player sender, Player target, CreatureType declaredCreature) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomCode}/play", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + sender.getId()
                                + "\",\"cardId\":\"" + sender.getHand().get(0).getId()
                                + "\",\"targetPlayerId\":\"" + target.getId()
                                + "\",\"declaredCreature\":\"" + declaredCreature.name() + "\"}"))
                .andExpect(status().isOk());
    }
}
