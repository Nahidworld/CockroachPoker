package com.cockroach.backend.model;

import com.cockroach.backend.enums.CreatureType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTests {

    @Test
    void constructorGeneratesIdAndKeepsName() {
        Player player = new Player("Alice");

        assertNotNull(player.getId());
        assertFalse(player.getId().isBlank());
        assertEquals("Alice", player.getName());
        assertTrue(player.getHand().isEmpty());
        assertTrue(player.getCollectedCards().isEmpty());
    }

    @Test
    void eachPlayerGetsADistinctSecretToken() {
        Player alice = new Player("Alice");
        Player bob = new Player("Bob");

        assertNotNull(alice.getToken());
        assertFalse(alice.getToken().isBlank());
        assertNotEquals(alice.getToken(), bob.getToken());
        assertNotEquals(alice.getId(), alice.getToken());
        assertTrue(alice.hasToken(alice.getToken()));
        assertFalse(alice.hasToken(bob.getToken()));
        assertFalse(alice.hasToken(alice.getId()));
        assertFalse(alice.hasToken(null));
    }

    @Test
    void constructorRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Player("  "));
    }

    @Test
    void addAndRemoveCardFromHand() {
        Player player = new Player("Alice");
        Card card = new Card(CreatureType.BAT);

        player.addCardToHand(card);

        assertTrue(player.hasCardInHand(card.getId()));
        assertEquals(List.of(card), player.getHand());

        Card removed = player.removeCardFromHand(card.getId());

        assertEquals(card, removed);
        assertFalse(player.hasCardInHand(card.getId()));
        assertTrue(player.getHand().isEmpty());
    }

    @Test
    void removeCardFromHandRejectsUnknownCard() {
        Player player = new Player("Alice");

        assertThrows(IllegalArgumentException.class, () -> player.removeCardFromHand("missing"));
    }

    @Test
    void addCollectedCard() {
        Player player = new Player("Alice");
        Card card = new Card(CreatureType.RAT);

        player.addCollectedCard(card);

        assertEquals(List.of(card), player.getCollectedCards());
    }

    @Test
    void handAndCollectedCardsAreReadOnly() {
        Player player = new Player("Alice");
        player.addCardToHand(new Card(CreatureType.FLY));
        player.addCollectedCard(new Card(CreatureType.TOAD));

        assertThrows(UnsupportedOperationException.class, () -> player.getHand().clear());
        assertThrows(UnsupportedOperationException.class, () -> player.getCollectedCards().clear());
    }
}
