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
    private final String latestClaimantId;
    private final CreatureType declaredCreature;
    private final String currentReceiverId;

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

    private static String requireId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
