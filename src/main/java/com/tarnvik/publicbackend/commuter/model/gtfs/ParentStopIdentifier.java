package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

public enum ParentStopIdentifier {
  SPANGA_STATION("9021001012138000", "Spånga station"),
  BALSTA("9021001006101000", "Bålsta"),
  ALVIK("9021001012025000", "Alvik"),
  HASSELBY_STRAND("9021001001331000", "Hässelby strand"),
  URBAN_HJARNES_VAG("9021001012281000", "Urban Hjärnes väg"),      // Genväg nattetid 117

  FARSTA_STRAND("9021001001881000", "Farsta strand"),
  SKARMARBRINK("9021001001601000", "Skärmarbrink"),
  SKARPNACK("9021001001951000", "Skarpnäck"),
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
