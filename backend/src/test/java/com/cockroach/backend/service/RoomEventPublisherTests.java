package com.cockroach.backend.service;

import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.dto.ErrorResponse;
import com.cockroach.backend.dto.GameEventResponse;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameEventType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RoomEventPublisherTests {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RoomEventPublisher publisher = new RoomEventPublisher(messagingTemplate);
    private final RoomService roomService =
            new RoomService(new CardDistributionService(new CardService()));

    @Test
    void cardPassedEventIsPublishedToRoomTopicWithoutCardIdentity() {
        Room room = createRoomWithPendingCard();
        String senderId = room.getPlayers().get(0).getId();

        publisher.publishCardPassed(room, senderId);

        GameEventResponse event = captureRoomEvent(room);
        assertEquals(GameEventType.CARD_PASSED, event.type());
        assertEquals(senderId, event.actorPlayerId());
        assertEquals(room.getPlayers().get(1).getId(), event.targetPlayerId());
        assertEquals(CreatureType.BAT, event.declaredCreature());
        assertNull(event.creatureType());
    }

    @Test
    void challengeResolvedEventExposesRevealedCreatureAfterResolution() {
        Room room = createRoomWithPendingCard();
        Player challenger = room.getPlayers().get(1);
        CreatureType actualType = pendingCreatureType(room);
        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        publisher.publishChallengeResolved(room, response);

        GameEventResponse event = captureRoomEvent(room);
        assertEquals(GameEventType.CHALLENGE_RESOLVED, event.type());
        assertEquals(challenger.getId(), event.actorPlayerId());
        assertEquals(actualType, event.creatureType());
    }

    @Test
    void gameFinishedEventCarriesLoser() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType actualType = card.getCreatureType();
        for (int i = 0; i < 3; i++) {
            challenger.addCollectedCard(new Card(actualType));
        }
        roomService.playCard(
                room.getCode(),
                claimant.getId(),
                card.getId(),
                challenger.getId(),
                actualType);
        roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        publisher.publishGameFinished(room);

        GameEventResponse event = captureRoomEvent(room);
        assertEquals(GameEventType.GAME_FINISHED, event.type());
        assertEquals(challenger.getId(), event.loserPlayerId());
        assertEquals(GameStatus.FINISHED, event.status());
    }

    @Test
    void peekResultIsSentOnlyToTheRequestingUserQueue() {
        PeekResponse response = new PeekResponse("ABCDEF", "player-1", CreatureType.BAT);

        publisher.sendPeekResult("session-user-1", response);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-user-1"), eq("/queue/peek"), eq(response));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void errorsAreSentOnlyToTheRequestingUserQueue() {
        ErrorResponse error = new ErrorResponse("INVALID_GAME_STATE", "It is not this player's turn");

        publisher.sendError("session-user-1", error);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-user-1"), eq("/queue/errors"), eq(error));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    private GameEventResponse captureRoomEvent(Room room) {
        ArgumentCaptor<GameEventResponse> captor = ArgumentCaptor.forClass(GameEventResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/" + room.getCode()), captor.capture());
        return captor.getValue();
    }

    private Room createRoomWithPendingCard() {
        Room room = createStartedGame();
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        roomService.playCard(
                room.getCode(),
                sender.getId(),
                sender.getHand().get(0).getId(),
                receiver.getId(),
                CreatureType.BAT);
        assertTrue(room.getGameState().hasPendingCard());
        return room;
    }

    private Room createStartedGame() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }

    private CreatureType pendingCreatureType(Room room) {
        return room.getGameState().getPendingCard().orElseThrow().getCard().getCreatureType();
    }
}
