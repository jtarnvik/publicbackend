package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

public enum ParentStopIdentifier {
  SPANGA_STATION("9021001012138000", "Spånga station"),
  BALSTA("9021001006101000", "Bålsta"),
  ALVIK("9021001012025000", "Alvik"),
  RANDOW("9021001006101000", ""), // See what this is and correct naming
  URBAN_HJARNES_VAG("9021001012281000", "Urban Hjärnes väg"),      // Genväg nattetid 117
  ;

  @Getter
  private final String id;
  @Getter
  private final String name;

  ParentStopIdentifier(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public static Optional<ParentStopIdentifier> fromStopId(String stopId) {
    return Arrays.stream(values()).filter(p -> p.id.equals(stopId)).findFirst();
  }
}
