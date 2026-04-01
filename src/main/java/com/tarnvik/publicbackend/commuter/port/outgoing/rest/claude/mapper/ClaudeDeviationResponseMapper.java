package com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.dto.ClaudeDeviationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.Optional;

@Mapper(componentModel = "spring")
public interface ClaudeDeviationResponseMapper {
  @Mapping(target = "fromDate", source = "from")
  @Mapping(target = "toDate", source = "to")
  DeviationResponse toDeviationResponse(ClaudeDeviationResponse claudeResponse);

  default LocalDate unwrapDate(Optional<LocalDate> optional) {
    return optional.orElse(null);
  }

  default Boolean unwrapBoolean(Optional<Boolean> optional) {
    return optional.orElse(null);
  }
}
