package com.cockroach.backend.service;

import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeResolutionTests {

    private final RoomService roomService =
            new RoomService(new CardDistributionService(new CardService()));

    @Test
    void correctTrueChallengeGivesCardToLatestClaimant() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType actualType = card.getCreatureType();
        playCard(room, claimant, challenger, actualType);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), true);

        assertTrue(response.correct());
        assertEquals(claimant.getId(), response.recipientId());
        assertEquals(actualType, response.declaredCreature());
        assertEquals(actualType, response.actualCreature());
        assertTrue(claimant.getCollectedCards().contains(card));
        assertTrue(challenger.getCollectedCards().isEmpty());
    }

    @Test
    void correctFalseChallengeGivesCardToLatestClaimant() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType declaredType = differentFrom(card.getCreatureType());
        playCard(room, claimant, challenger, declaredType);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertTrue(response.correct());
        assertEquals(claimant.getId(), response.recipientId());
        assertEquals(declaredType, response.declaredCreature());
        assertEquals(card.getCreatureType(), response.actualCreature());
        assertTrue(claimant.getCollectedCards().contains(card));
        assertTrue(challenger.getCollectedCards().isEmpty());
    }

    @Test
    void incorrectTrueChallengeGivesCardToChallenger() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType declaredType = differentFrom(card.getCreatureType());
        playCard(room, claimant, challenger, declaredType);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), true);

        assertFalse(response.correct());
        assertEquals(challenger.getId(), response.recipientId());
        assertTrue(challenger.getCollectedCards().contains(card));
        assertTrue(claimant.getCollectedCards().isEmpty());
    }

    @Test
    void incorrectFalseChallengeGivesCardToChallenger() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        playCard(room, claimant, challenger, card.getCreatureType());

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertFalse(response.correct());
        assertEquals(challenger.getId(), response.recipientId());
        assertTrue(challenger.getCollectedCards().contains(card));
        assertTrue(claimant.getCollectedCards().isEmpty());
    }

    @Test
    void resolvingChallengeClearsPendingCardAndRecipientStartsNextRound() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        playCard(room, claimant, challenger, differentFrom(card.getCreatureType()));

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertTrue(room.getGameState().getPendingCard().isEmpty());
        assertFalse(room.getGameState().hasPendingCard());
        assertEquals(response.recipientId(), room.getGameState().getCurrentPlayerId());
        assertEquals(claimant.getId(), room.getGameState().getCurrentPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertNull(response.loserPlayerId());
    }

    @Test
    void fourMatchingCollectedCardsFinishGameAndRecordLoser() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType creatureType = card.getCreatureType();

        for (int i = 0; i < 3; i++) {
            challenger.addCollectedCard(new Card(creatureType));
        }
        playCard(room, claimant, challenger, creatureType);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertFalse(response.correct());
        assertEquals(challenger.getId(), response.recipientId());
        assertEquals(challenger.getId(), response.loserPlayerId());
        assertEquals(4, challenger.countCollectedCards(creatureType));
        assertEquals(GameStatus.FINISHED, room.getStatus());
        assertEquals(challenger.getId(), room.getGameState().getLoserPlayerId());
        assertNull(room.getGameState().getCurrentPlayerId());
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void threeMatchingCollectedCardsDoNotFinishGame() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType collectedType = differentFrom(card.getCreatureType());
        for (int i = 0; i < 3; i++) {
            challenger.addCollectedCard(new Card(collectedType));
        }
        playCard(room, claimant, challenger, card.getCreatureType());

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertEquals(challenger.getId(), response.recipientId());
        assertNull(response.loserPlayerId());
        assertEquals(3, challenger.countCollectedCards(collectedType));
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertEquals(challenger.getId(), room.getGameState().getCurrentPlayerId());
        assertNull(room.getGameState().getLoserPlayerId());
    }

    @Test
    void emptyHandAtRoundStartFinishesGameAndRecordsLoser() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        emptyHand(challenger);
        playCard(room, claimant, challenger, card.getCreatureType());

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertEquals(challenger.getId(), response.recipientId());
        assertTrue(challenger.getHand().isEmpty());
        assertEquals(challenger.getId(), response.loserPlayerId());
        assertEquals(GameStatus.FINISHED, room.getStatus());
        assertEquals(challenger.getId(), room.getGameState().getLoserPlayerId());
        assertNull(room.getGameState().getCurrentPlayerId());
        assertNull(response.currentPlayerId());
    }

    @Test
    void finishedGameRejectsAllFurtherGameplayActions() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType creatureType = card.getCreatureType();

        for (int i = 0; i < 3; i++) {
            challenger.addCollectedCard(new Card(creatureType));
        }
        playCard(room, claimant, challenger, creatureType);
        roomService.resolveChallenge(room.getCode(), challenger.getId(), false);
        assertEquals(GameStatus.FINISHED, room.getStatus());

        String remainingCardId = claimant.getHand().get(0).getId();
        assertThrows(InvalidGameStateException.class, () -> roomService.playCard(
                room.getCode(), claimant.getId(), remainingCardId, challenger.getId(), CreatureType.BAT));
        assertThrows(InvalidGameStateException.class,
                () -> roomService.peekCard(room.getCode(), claimant.getId()));
        assertThrows(InvalidGameStateException.class,
                () -> roomService.passCard(room.getCode(), claimant.getId(), challenger.getId()));
        assertThrows(InvalidGameStateException.class,
                () -> roomService.resolveChallenge(room.getCode(), claimant.getId(), true));
        assertThrows(GameAlreadyStartedException.class,
                () -> roomService.startGame(room.getCode(), room.getHostPlayerId()));

        assertEquals(GameStatus.FINISHED, room.getStatus());
        assertEquals(challenger.getId(), room.getGameState().getLoserPlayerId());
        assertTrue(room.getGameState().getPendingCard().isEmpty());
    }

    @Test
    void challengeRejectsUnknownRoom() {
        assertThrows(RoomNotFoundException.class,
                () -> roomService.resolveChallenge("ZZZZZZ", "player-id", true));
    }

    @Test
    void challengeRejectsGameThatIsNotInProgress() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");

        assertThrows(InvalidGameStateException.class,
                () -> roomService.resolveChallenge(room.getCode(), room.getPlayers().get(1).getId(), true));
    }

    @Test
    void challengeRejectsWhenThereIsNoPendingCard() {
        Room room = createStartedGame();

        assertThrows(InvalidGameStateException.class,
                () -> roomService.resolveChallenge(room.getCode(), room.getPlayers().get(1).getId(), true));
    }

    @Test
    void challengeRejectsPlayerOtherThanCurrentReceiver() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        playCard(room, claimant, challenger, card.getCreatureType());

        assertThrows(InvalidGameStateException.class,
                () -> roomService.resolveChallenge(room.getCode(), claimant.getId(), true));
        assertTrue(room.getGameState().hasPendingCard());
    }

    @Test
    void challengeRejectsMissingGuess() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        playCard(room, claimant, challenger, card.getCreatureType());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.resolveChallenge(room.getCode(), challenger.getId(), null));
        assertTrue(room.getGameState().hasPendingCard());
    }

    @Test
    void challengeRejectsBlankPlayerId() {
        Room room = createStartedGame();

        assertThrows(IllegalArgumentException.class,
                () -> roomService.resolveChallenge(room.getCode(), null, true));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.resolveChallenge(room.getCode(), "   ", true));
    }

    @Test
    void challengeRejectsResolutionAfterGameIsFinished() {
        Room room = createStartedGame();
        Player claimant = room.getPlayers().get(0);
        Player challenger = room.getPlayers().get(1);
        Card card = claimant.getHand().get(0);
        CreatureType creatureType = card.getCreatureType();

        for (int i = 0; i < 3; i++) {
            challenger.addCollectedCard(new Card(creatureType));
        }
        playCard(room, claimant, challenger, creatureType);
        roomService.resolveChallenge(room.getCode(), challenger.getId(), false);

        assertThrows(InvalidGameStateException.class,
                () -> roomService.resolveChallenge(room.getCode(), challenger.getId(), true));
    }

    @Test
    void correctChallengeAfterPassPenalizesLatestClaimant() {
        Room room = createStartedGame(3);
        Player originalSender = room.getPlayers().get(0);
        Player firstReceiver = room.getPlayers().get(1);
        Player secondReceiver = room.getPlayers().get(2);
        Card card = originalSender.getHand().get(0);
        playCard(room, originalSender, firstReceiver, card.getCreatureType());
        roomService.peekCard(room.getCode(), firstReceiver.getId());
        roomService.passCard(room.getCode(), firstReceiver.getId(), secondReceiver.getId());

        ChallengeResponse response = roomService.resolveChallenge(
                room.getCode(), secondReceiver.getId(), true);

        assertTrue(response.correct());
        assertEquals(firstReceiver.getId(), response.recipientId());
        assertTrue(firstReceiver.getCollectedCards().contains(card));
        assertTrue(originalSender.getCollectedCards().isEmpty());
        assertTrue(secondReceiver.getCollectedCards().isEmpty());
    }

    private Room createStartedGame() {
        return createStartedGame(2);
    }

    private Room createStartedGame(int playerCount) {
        Room room = roomService.createRoom("Alice");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }

    private void playCard(Room room, Player sender, Player receiver, CreatureType declaredType) {
        roomService.playCard(
                room.getCode(),
                sender.getId(),
                sender.getHand().get(0).getId(),
                receiver.getId(),
                declaredType);
    }

    private void emptyHand(Player player) {
        for (Card card : List.copyOf(player.getHand())) {
            player.removeCardFromHand(card.getId());
        }
    }

    private CreatureType differentFrom(CreatureType creatureType) {
        return Arrays.stream(CreatureType.values())
                .filter(candidate -> candidate != creatureType)
                .findFirst()
                .orElseThrow();
    }
}
