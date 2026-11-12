package com.cockroach.backend.service;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardServiceTests {

    private final CardService cardService = new CardService();

    @Test
    void createDeckReturnsSixtyFourCards() {
        List<Card> deck = cardService.createDeck();

        assertEquals(64, deck.size());
    }

    @Test
    void createDeckContainsEightCardsOfEachCreature() {
        List<Card> deck = cardService.createDeck();

        Map<CreatureType, Long> counts = new EnumMap<>(CreatureType.class);
        for (CreatureType creatureType : CreatureType.values()) {
            counts.put(creatureType, 0L);
        }
        for (Card card : deck) {
            counts.merge(card.getCreatureType(), 1L, Long::sum);
        }

        assertEquals(CreatureType.values().length, counts.size());
        for (CreatureType creatureType : CreatureType.values()) {
            assertEquals(8L, counts.get(creatureType), "Unexpected count for " + creatureType);
        }
    }

    @Test
    void createDeckHasUniqueCardIds() {
        List<Card> deck = cardService.createDeck();

        Set<String> ids = new HashSet<>();
        for (Card card : deck) {
            ids.add(card.getId());
        }

        assertEquals(deck.size(), ids.size());
    }
}
