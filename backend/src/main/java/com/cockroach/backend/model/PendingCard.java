package com.cockroach.backend.model;

import com.cockroach.backend.enums.CreatureType;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
public class PendingCard {

    private final Card card;
    private final String originalSenderId;
    private String latestClaimantId;
    private CreatureType declaredCreature;
    private String currentReceiverId;

    @Getter(AccessLevel.NONE)
    private final Set<String> seenPlayerIds = new LinkedHashSet<>();

    public PendingCard(
            Card card,
            String originalSenderId,
            String latestClaimantId,
            CreatureType declaredCreature,
            String currentReceiverId) {
        this.card = Objects.requireNonNull(card, "card must not be null");
        this.originalSenderId = requireId(originalSenderId, "originalSenderId");
        this.latestClaimantId = requireId(latestClaimantId, "latestClaimantId");
        this.declaredCreature = Objects.requireNonNull(declaredCreature, "declaredCreature must not be null");
        this.currentReceiverId = requireId(currentReceiverId, "currentReceiverId");
        this.seenPlayerIds.add(this.originalSenderId);
    }

    public Set<String> getSeenPlayerIds() {
        return Collections.unmodifiableSet(seenPlayerIds);
    }

    public boolean hasSeen(String playerId) {
        return playerId != null && seenPlayerIds.contains(playerId);
    }

    public void markSeen(String playerId) {
        String id = requireId(playerId, "playerId");
        if (!seenPlayerIds.add(id)) {
            throw new IllegalStateException("Player has already seen the pending card: " + id);
        }
    }

    /**
     * Passes the card on. The passer becomes the latest claimant and makes their own claim,
     * which may differ from the previous one.
     */
    public void passTo(String latestClaimantId, String newReceiverId, CreatureType newDeclaredCreature) {
        String claimantId = requireId(latestClaimantId, "latestClaimantId");
        String receiverId = requireId(newReceiverId, "newReceiverId");
        Objects.requireNonNull(newDeclaredCreature, "declaredCreature must not be null");
        if (claimantId.equals(receiverId)) {
            throw new IllegalArgumentException("A player cannot pass a card to themselves");
        }
        if (seenPlayerIds.contains(receiverId)) {
            throw new IllegalArgumentException("Target has already seen the pending card: " + receiverId);
        }
        this.latestClaimantId = claimantId;
        this.currentReceiverId = receiverId;
        this.declaredCreature = newDeclaredCreature;
    }

    private static String requireId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
