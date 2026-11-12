package com.cockroach.backend.service;

import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.model.Card;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CardService {

    private static final int CARDS_PER_CREATURE = 8;

    public List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();

        for (CreatureType creatureType : CreatureType.values()) {
            for (int i = 0; i < CARDS_PER_CREATURE; i++) {
                deck.add(new Card(creatureType));
            }
        }

        Collections.shuffle(deck);
        return deck;
    }
}
