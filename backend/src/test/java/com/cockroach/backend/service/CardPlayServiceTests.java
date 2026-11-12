package com.cockroach.backend.service;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
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

class CardPlayServiceTests {

    private final RoomService roomService =
            new RoomService(new CardDistributionService(new CardService()));

    @Test
    void playCardCreatesPendingCardAndRemovesCardFromHand() {
        Room room = createStartedRoom(3);
        Player sender = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);
        Card card = sender.getHand().get(0);
        int handSizeBefore = sender.getHand().size();

        Room result = roomService.playCard(
                room.getCode(), sender.getId(), card.getId(), target.getId(), CreatureType.BAT);

        assertSame(room, result);
        assertEquals(handSizeBefore - 1, sender.getHand().size());
        assertFalse(sender.hasCardInHand(card.getId()));

        PendingCard pendingCard = room.getGameState().getPendingCard().orElseThrow();
        assertSame(card, pendingCard.getCard());
        assertEquals(sender.getId(), pendingCard.getOriginalSenderId());
        assertEquals(sender.getId(), pendingCard.getLatestClaimantId());
        assertEquals(CreatureType.BAT, pendingCard.getDeclaredCreature());
        assertEquals(Set.of(sender.getId()), pendingCard.getSeenPlayerIds());
        assertEquals(target.getId(), pendingCard.getCurrentReceiverId());

        assertEquals(target.getId(), room.getGameState().getCurrentPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
    }

    @Test
    void playCardRejectsCardNotOwnedBySender() {
        Room room = createStartedRoom(3);
        Player sender = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);
        Card cardOwnedByTarget = target.getHand().get(0);
        int senderHandSize = sender.getHand().size();

        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), cardOwnedByTarget.getId(), target.getId(), CreatureType.RAT));

        assertEquals(senderHandSize, sender.getHand().size());
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void playCardRejectsPlayerWhoseTurnItIsNot() {
        Room room = createStartedRoom(3);
        Player currentPlayer = room.getPlayers().get(0);
        Player otherPlayer = room.getPlayers().get(1);
        Card card = otherPlayer.getHand().get(0);

        assertThrows(InvalidGameStateException.class, () -> roomService.playCard(
                room.getCode(), otherPlayer.getId(), card.getId(), currentPlayer.getId(), CreatureType.FLY));

        assertTrue(otherPlayer.hasCardInHand(card.getId()));
        assertTrue(room.getGameState().getPendingCard().isEmpty());
        assertEquals(currentPlayer.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void playCardRejectsUnknownTarget() {
        Room room = createStartedRoom(3);
        Player sender = room.getPlayers().get(0);
        Card card = sender.getHand().get(0);

        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), card.getId(), "missing-player", CreatureType.TOAD));

        assertTrue(sender.hasCardInHand(card.getId()));
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void playCardRejectsSelfTargeting() {
        Room room = createStartedRoom(3);
        Player sender = room.getPlayers().get(0);
        Card card = sender.getHand().get(0);

        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), card.getId(), sender.getId(), CreatureType.SPIDER));

        assertTrue(sender.hasCardInHand(card.getId()));
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void playCardRejectsGameThatIsNotInProgress() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");
        Player sender = room.getPlayers().get(0);

        assertThrows(InvalidGameStateException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), "missing-card",
                room.getPlayers().get(1).getId(), CreatureType.BAT));

        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void playCardRejectsUnknownRoom() {
        assertThrows(RoomNotFoundException.class, () -> roomService.playCard(
                "ZZZZZZ", "player-id", "card-id", "target-id", CreatureType.BAT));
    }

    @Test
    void playCardRejectsMissingRequiredInformation() {
        Room room = createStartedRoom(2);
        Player sender = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);
        String cardId = sender.getHand().get(0).getId();

        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), null, cardId, target.getId(), CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), "   ", cardId, target.getId(), CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), null, target.getId(), CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), "   ", target.getId(), CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), cardId, null, CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), cardId, "   ", CreatureType.BAT));
        assertThrows(IllegalArgumentException.class, () -> roomService.playCard(
                room.getCode(), sender.getId(), cardId, target.getId(), null));

        assertTrue(sender.hasCardInHand(cardId));
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void playCardRejectsSecondPlayWhilePendingCardExists() {
        Room room = createStartedRoom(3);
        Player sender = room.getPlayers().get(0);
        Player receiver = room.getPlayers().get(1);
        Player thirdPlayer = room.getPlayers().get(2);
        Card card = sender.getHand().get(0);
        roomService.playCard(
                room.getCode(), sender.getId(), card.getId(), receiver.getId(), CreatureType.BAT);

        Card receiverCard = receiver.getHand().get(0);

        assertThrows(InvalidGameStateException.class, () -> roomService.playCard(
                room.getCode(), receiver.getId(), receiverCard.getId(), thirdPlayer.getId(), CreatureType.FLY));

        assertTrue(receiver.hasCardInHand(receiverCard.getId()));
    }

    private Room createStartedRoom(int playerCount) {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }
}
