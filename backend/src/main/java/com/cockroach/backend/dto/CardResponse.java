package com.cockroach.backend.dto;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A card in the caller's own hand")
public record CardResponse(
        @Schema(description = "Card ID", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        String id,

        @Schema(description = "Card creature type", example = "FLY")
        CreatureType creatureType
) {

    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getCreatureType());
    }
}
