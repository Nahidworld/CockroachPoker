package com.cockroach.backend.service;

import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeekAndPassTests {

    private final RoomService roomService =
            new RoomService(new CardDistributionService(new CardService()));

    @Test
    void peekRevealsActualCreatureTypeAndMarksPlayerAsSeen() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Card card = sender.getHand().get(0);
        playCard(room, sender, receiver);

        PeekResponse response = roomService.peekCard(room.getCode(), receiver.getId());

        assertEquals(room.getCode(), response.roomCode());
        assertEquals(receiver.getId(), response.playerId());
        assertEquals(card.getCreatureType(), response.actualCreature());

        PendingCard pendingCard = pendingCard(room);
        assertTrue(pendingCard.hasSeen(receiver.getId()));
        assertEquals(Set.of(sender.getId(), receiver.getId()), pendingCard.getSeenPlayerIds());
        assertEquals(receiver.getId(), pendingCard.getCurrentReceiverId());
        assertEquals(receiver.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void peekRejectsPlayerOtherThanCurrentReceiver() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);

        assertThrows(InvalidGameStateException.class,
                () -> roomService.peekCard(room.getCode(), sender.getId()));
        PendingCard pendingCard = pendingCard(room);
        assertFalse(pendingCard.hasSeen(receiver.getId()));
        assertEquals(Set.of(sender.getId()), pendingCard.getSeenPlayerIds());
        assertEquals(receiver.getId(), pendingCard.getCurrentReceiverId());
    }

    @Test
    void peekRejectsRepeatedPeek() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        assertThrows(InvalidGameStateException.class,
                () -> roomService.peekCard(room.getCode(), receiver.getId()));
    }

    @Test
    void passAfterPeekUpdatesReceiverAndLatestClaimant() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Player nextReceiver = room.getPlayers().get(2);
        playCard(room, sender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        Room result = roomService.passCard(room.getCode(), receiver.getId(), nextReceiver.getId());

        assertSame(room, result);
        PendingCard pendingCard = pendingCard(room);
        assertEquals(sender.getId(), pendingCard.getOriginalSenderId());
        assertEquals(receiver.getId(), pendingCard.getLatestClaimantId());
        assertEquals(nextReceiver.getId(), pendingCard.getCurrentReceiverId());
        assertEquals(Set.of(sender.getId(), receiver.getId()), pendingCard.getSeenPlayerIds());
        assertFalse(pendingCard.hasSeen(nextReceiver.getId()));
        assertEquals(nextReceiver.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void passPreservesSeenHistoryAcrossMultiplePasses() {
        Room room = createStartedGame(4);
        Player originalSender = room.getPlayers().get(0);
        Player firstReceiver = room.getPlayers().get(1);
        Player secondReceiver = room.getPlayers().get(2);
        Player thirdReceiver = room.getPlayers().get(3);
        playCard(room, originalSender, firstReceiver);
        roomService.peekCard(room.getCode(), firstReceiver.getId());
        roomService.passCard(room.getCode(), firstReceiver.getId(), secondReceiver.getId());
        roomService.peekCard(room.getCode(), secondReceiver.getId());

        roomService.passCard(room.getCode(), secondReceiver.getId(), thirdReceiver.getId());

        PendingCard pendingCard = pendingCard(room);
        assertEquals(originalSender.getId(), pendingCard.getOriginalSenderId());
        assertEquals(secondReceiver.getId(), pendingCard.getLatestClaimantId());
        assertEquals(thirdReceiver.getId(), pendingCard.getCurrentReceiverId());
        assertEquals(Set.of(originalSender.getId(), firstReceiver.getId(), secondReceiver.getId()),
                pendingCard.getSeenPlayerIds());
        assertEquals(thirdReceiver.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void passBeforePeekIsRejected() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Player nextReceiver = room.getPlayers().get(2);
        playCard(room, sender, receiver);

        assertThrows(InvalidGameStateException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), nextReceiver.getId()));

        PendingCard pendingCard = pendingCard(room);
        assertEquals(sender.getId(), pendingCard.getLatestClaimantId());
        assertEquals(receiver.getId(), pendingCard.getCurrentReceiverId());
        assertEquals(receiver.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void passRejectsSelfTargeting() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), receiver.getId()));
        assertEquals(receiver.getId(), pendingCard(room).getCurrentReceiverId());
    }

    @Test
    void passRejectsUnknownTarget() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), "missing-player"));
        assertEquals(receiver.getId(), pendingCard(room).getCurrentReceiverId());
    }

    @Test
    void passRejectsPreviouslySeenTarget() {
        Room room = createStartedGame(3);
        Player originalSender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, originalSender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), originalSender.getId()));
        assertEquals(receiver.getId(), pendingCard(room).getCurrentReceiverId());
    }

    @Test
    void lastEligiblePlayerCannotPass() {
        Room room = createStartedGame(2);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);
        roomService.peekCard(room.getCode(), receiver.getId());

        assertThrows(InvalidGameStateException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), sender.getId()));
        assertEquals(receiver.getId(), pendingCard(room).getCurrentReceiverId());
    }

    @Test
    void peekAndPassRejectUnknownRoom() {
        assertThrows(RoomNotFoundException.class,
                () -> roomService.peekCard("ZZZZZZ", "player-id"));
        assertThrows(RoomNotFoundException.class,
                () -> roomService.passCard("ZZZZZZ", "player-id", "target-id"));
    }

    @Test
    void peekAndPassRejectGameThatIsNotInProgress() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");
        Player receiver = room.getPlayers().get(1);

        assertThrows(InvalidGameStateException.class,
                () -> roomService.peekCard(room.getCode(), receiver.getId()));
        assertThrows(InvalidGameStateException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), room.getPlayers().get(0).getId()));
    }

    @Test
    void peekAndPassRejectWhenThereIsNoPendingCard() {
        Room room = createStartedGame(2);
        Player player = room.getPlayers().get(0);

        assertThrows(InvalidGameStateException.class,
                () -> roomService.peekCard(room.getCode(), player.getId()));
        assertThrows(InvalidGameStateException.class,
                () -> roomService.passCard(room.getCode(), player.getId(), room.getPlayers().get(1).getId()));
    }

    @Test
    void peekAndPassRejectBlankRequiredInformation() {
        Room room = createStartedGame(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        playCard(room, sender, receiver);

        assertThrows(IllegalArgumentException.class,
                () -> roomService.peekCard(room.getCode(), null));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.peekCard(room.getCode(), "   "));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), receiver.getId(), "   "));
    }

    private Room createStartedGame(int playerCount) {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }

    private void playCard(Room room, Player sender, Player receiver) {
        roomService.playCard(
                room.getCode(),
                sender.getId(),
                sender.getHand().get(0).getId(),
                receiver.getId(),
                CreatureType.BAT);
    }

    private PendingCard pendingCard(Room room) {
        return room.getGameState().getPendingCard().orElseThrow();
    }
}
