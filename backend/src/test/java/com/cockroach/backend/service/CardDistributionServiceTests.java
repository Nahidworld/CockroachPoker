package com.cockroach.backend.service;

import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardDistributionServiceTests {

    private final CardDistributionService cardDistributionService =
            new CardDistributionService(new CardService());

    @Test
    void distributesThirtyTwoCardsToEachPlayerForTwoPlayers() {
        assertDistribution(2, 32, 32);
    }

    @Test
    void distributesTwentyTwoTwentyOneTwentyOneForThreePlayers() {
        assertDistribution(3, 22, 21, 21);
    }

    @Test
    void distributesSixteenCardsToEachPlayerForFourPlayers() {
        assertDistribution(4, 16, 16, 16, 16);
    }

    @Test
    void distributesRemainderToFirstPlayersForFivePlayers() {
        assertDistribution(5, 13, 13, 13, 13, 12);
    }

    @Test
    void distributesRemainderToFirstPlayersForSixPlayers() {
        assertDistribution(6, 11, 11, 11, 11, 10, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void distributesAllSixtyFourCards(int playerCount) {
        List<Player> players = distributeToGame(playerCount);

        assertEquals(64, collectCards(players).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void distributedCardIdsAreUnique(int playerCount) {
        List<Player> players = distributeToGame(playerCount);
        List<Card> cards = collectCards(players);

        Set<String> cardIds = new HashSet<>();
        for (Card card : cards) {
            cardIds.add(card.getId());
        }

        assertEquals(cards.size(), cardIds.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsTooFewPlayers(int playerCount) {
        List<Player> players = createPlayers(playerCount);

        assertThrows(IllegalArgumentException.class,
                () -> cardDistributionService.distributeCards(players));
    }

    @Test
    void rejectsTooManyPlayers() {
        List<Player> players = createPlayers(7);

        assertThrows(IllegalArgumentException.class,
                () -> cardDistributionService.distributeCards(players));
    }

    private void assertDistribution(int playerCount, int... expectedHandSizes) {
        List<Player> players = distributeToGame(playerCount);

        assertEquals(expectedHandSizes.length, players.size());
        for (int i = 0; i < expectedHandSizes.length; i++) {
            assertEquals(expectedHandSizes[i], players.get(i).getHand().size(),
                    "Unexpected hand size for player " + (i + 1));
        }
    }

    private List<Player> distributeToGame(int playerCount) {
        GameState gameState = new GameState();
        createPlayers(playerCount).forEach(gameState::addPlayer);

        cardDistributionService.distributeCards(gameState);

        return gameState.getPlayers();
    }

    private List<Player> createPlayers(int playerCount) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new Player("Player " + (i + 1)));
        }
        return players;
    }

    private List<Card> collectCards(List<Player> players) {
        List<Card> cards = new ArrayList<>();
        for (Player player : players) {
            cards.addAll(player.getHand());
        }
        return cards;
    }
}
