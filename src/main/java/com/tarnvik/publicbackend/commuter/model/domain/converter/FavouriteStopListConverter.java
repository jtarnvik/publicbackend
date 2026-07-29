package com.tarnvik.publicbackend.commuter.model.domain.converter;

import com.tarnvik.publicbackend.commuter.model.domain.FavouriteStop;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;

import java.util.Collections;
import java.util.List;

/**
 * Stores favourite stops as JSON in a TEXT column. Structurally identical to
 * {@link RecentStopListConverter} — an {@link AttributeConverter} is bound to a concrete type pair, so the
 * existing one cannot be reused for a different element type.
 */
@Converter
public class FavouriteStopListConverter implements AttributeConverter<List<FavouriteStop>, String> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CollectionType LIST_TYPE =
    MAPPER.getTypeFactory().constructCollectionType(List.class, FavouriteStop.class);

  @Override
  public String convertToDatabaseColumn(List<FavouriteStop> favouriteStops) {
    if (favouriteStops == null || favouriteStops.isEmpty()) {
      return null;
    }
    return MAPPER.writeValueAsString(favouriteStops);
  }

  @Override
  public List<FavouriteStop> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return Collections.emptyList();
    }
    return MAPPER.readValue(dbData, LIST_TYPE);
  }
}
