package com.tarnvik.publicbackend.commuter.model.domain.converter;

import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;

import java.util.Collections;
import java.util.List;

@Converter
public class RecentStopListConverter implements AttributeConverter<List<RecentStop>, String> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CollectionType LIST_TYPE =
    MAPPER.getTypeFactory().constructCollectionType(List.class, RecentStop.class);

  @Override
  public String convertToDatabaseColumn(List<RecentStop> recentStops) {
    if (recentStops == null || recentStops.isEmpty()) {
      return null;
    }
    return MAPPER.writeValueAsString(recentStops);
  }

  @Override
  public List<RecentStop> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return Collections.emptyList();
    }
    return MAPPER.readValue(dbData, LIST_TYPE);
  }
}
