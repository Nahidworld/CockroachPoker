package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;

public record CardResponse(String id, CreatureType creatureType) {

    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getCreatureType());
    }
}
