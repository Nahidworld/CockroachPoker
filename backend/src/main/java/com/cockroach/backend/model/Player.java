package com.cockroach.backend.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Getter
public class Player {

    private final String id;
    private final String name;
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> collectedCards = new ArrayList<>();

    public Player(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public Player(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Player id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.id = id;
        this.name = name;
    }

    public List<Card> getHand() {
        return Collections.unmodifiableList(hand);
    }

    public List<Card> getCollectedCards() {
        return Collections.unmodifiableList(collectedCards);
    }

    public void addCardToHand(Card card) {
        Objects.requireNonNull(card, "card must not be null");
        hand.add(card);
    }

    public Card removeCardFromHand(String cardId) {
        Card card = findCardInHand(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found in hand: " + cardId));
        hand.remove(card);
        return card;
    }

    public boolean hasCardInHand(String cardId) {
        return findCardInHand(cardId).isPresent();
    }

    public Optional<Card> findCardInHand(String cardId) {
        Objects.requireNonNull(cardId, "cardId must not be null");
        return hand.stream()
                .filter(card -> card.getId().equals(cardId))
                .findFirst();
    }

    public void addCollectedCard(Card card) {
        Objects.requireNonNull(card, "card must not be null");
        collectedCards.add(card);
    }
}
