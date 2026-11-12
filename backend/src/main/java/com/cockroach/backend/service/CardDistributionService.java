package com.cockroach.backend.service;

import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CardDistributionService {

    private final CardService cardService;

    public void distributeCards(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        distributeCards(gameState.getPlayers());
    }

    public void distributeCards(List<Player> players) {
        Objects.requireNonNull(players, "players must not be null");

        int playerCount = players.size();
        if (playerCount < GameState.MIN_PLAYERS || playerCount > GameState.MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Card distribution requires between " + GameState.MIN_PLAYERS
                            + " and " + GameState.MAX_PLAYERS + " players, but received " + playerCount);
        }

        List<Card> deck = cardService.createDeck();
        int cardsPerPlayer = deck.size() / playerCount;
        int remainder = deck.size() % playerCount;

        int cardIndex = 0;
        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);
            int cardsForPlayer = cardsPerPlayer + (i < remainder ? 1 : 0);
            for (int j = 0; j < cardsForPlayer; j++) {
                player.addCardToHand(deck.get(cardIndex++));
            }
        }
    }
}
