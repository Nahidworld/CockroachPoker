package com.cockroach.backend.model;

import com.cockroach.backend.enums.CreatureType;

import java.util.UUID;

public class Card {

    private final String id;
    private final CreatureType creatureType;

    public Card(CreatureType creatureType) {
        this.id = UUID.randomUUID().toString();
        this.creatureType = creatureType;
    }

    public String getId() {
        return id;
    }

    public CreatureType getCreatureType() {
        return creatureType;
    }
}
