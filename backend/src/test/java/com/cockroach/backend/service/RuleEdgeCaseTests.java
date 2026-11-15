package com.cockroach.backend.service;

import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LossReason;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Edge cases of the published Cockroach Poker rules that the other tests do not cover:
 * players with an empty hand, two-player games, and passing back to someone who has seen the card.
 */
class RuleEdgeCaseTests {

    private final RoomService roomService = new RoomService(new CardDistributionService(new CardService()));

    @Test
    void playerWithEmptyHandCanStillReceivePeekAndPass() {
        Room room = startedGame(3);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        Player carol = player(room, 2);
        emptyHand(bob);
        play(room, alice, bob, true);

        roomService.peekCard(room.getCode(), bob.getId());
        roomService.passCard(room.getCode(), bob.getId(), carol.getId(), CreatureType.BAT);

        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertEquals(carol.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void playerWithEmptyHandWhoTakesTheCardLosesBecauseTheyMustStartTheNextRound() {
        Room room = startedGame(3);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        emptyHand(bob);
        play(room, alice, bob, true);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), bob.getId(), false);

        assertEquals(bob.getId(), response.recipientId());
        assertEquals(GameStatus.FINISHED, room.getStatus());
        assertEquals(bob.getId(), room.getGameState().getLoserPlayerId());
        assertEquals(LossReason.EMPTY_HAND, room.getGameState().getLossReason());
    }

    @Test
    void playerWithEmptyHandWhoCallsCorrectlyStaysInTheGame() {
        Room room = startedGame(3);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        emptyHand(bob);
        play(room, alice, bob, true);

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), bob.getId(), true);

        assertEquals(alice.getId(), response.recipientId());
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertEquals(alice.getId(), room.getGameState().getCurrentPlayerId());
    }

    @Test
    void inATwoPlayerGameTheReceiverCannotPeekAndMustCall() {
        Room room = startedGame(2);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        play(room, alice, bob, false);

        assertThrows(InvalidGameStateException.class, () -> roomService.peekCard(room.getCode(), bob.getId()));

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), bob.getId(), false);
        assertEquals(alice.getId(), response.recipientId());
    }

    @Test
    void cardCannotBePassedBackToAnyoneWhoHasSeenIt() {
        Room room = startedGame(4);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        Player carol = player(room, 2);
        play(room, alice, bob, true);
        roomService.peekCard(room.getCode(), bob.getId());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), bob.getId(), alice.getId(), CreatureType.RAT));

        roomService.passCard(room.getCode(), bob.getId(), carol.getId(), CreatureType.RAT);
        roomService.peekCard(room.getCode(), carol.getId());

        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), carol.getId(), bob.getId(), CreatureType.RAT));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.passCard(room.getCode(), carol.getId(), alice.getId(), CreatureType.RAT));
    }

    @Test
    void wrongCallAfterAPassGivesTheCardToTheCallerWhoStartsTheNextRound() {
        Room room = startedGame(4);
        Player alice = player(room, 0);
        Player bob = player(room, 1);
        Player carol = player(room, 2);
        Card card = play(room, alice, bob, true);
        roomService.peekCard(room.getCode(), bob.getId());
        roomService.passCard(room.getCode(), bob.getId(), carol.getId(), anotherCreature(card.getCreatureType()));

        ChallengeResponse response = roomService.resolveChallenge(room.getCode(), carol.getId(), true);

        assertEquals(carol.getId(), response.recipientId());
        assertEquals(1, carol.countCollectedCards(card.getCreatureType()));
        assertEquals(carol.getId(), room.getGameState().getCurrentPlayerId());
    }

    private Room startedGame(int playerCount) {
        Room room = roomService.createRoom("Player 0");
        for (int i = 1; i < playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        return room;
    }

    private Player player(Room room, int index) {
        return room.getPlayers().get(index);
    }

    private void emptyHand(Player player) {
        for (Card card : List.copyOf(player.getHand())) {
            player.removeCardFromHand(card.getId());
        }
    }

    /** Plays the sender's first card to the receiver, naming its real creature or a different one. */
    private Card play(Room room, Player sender, Player receiver, boolean truthful) {
        Card card = sender.getHand().get(0);
        CreatureType claim = truthful ? card.getCreatureType() : anotherCreature(card.getCreatureType());
        roomService.playCard(room.getCode(), sender.getId(), card.getId(), receiver.getId(), claim);
        return card;
    }

    private CreatureType anotherCreature(CreatureType creatureType) {
        CreatureType[] all = CreatureType.values();
        return all[(creatureType.ordinal() + 1) % all.length];
    }
}
