package com.cockroach.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The eight creature types in the deck; eight cards each")
public enum CreatureType {
    COCKROACH,
    STINK_BUG,
    BAT,
    FLY,
    TOAD,
    RAT,
    SCORPION,
    SPIDER
}
