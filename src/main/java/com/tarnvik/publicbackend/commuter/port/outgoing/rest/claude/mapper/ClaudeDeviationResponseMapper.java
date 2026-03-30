package com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.dto.ClaudeDeviationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClaudeDeviationResponseMapper {

  @Mapping(target = "fromDate", source = "from")
  @Mapping(target = "toDate", source = "to")
  DeviationResponse toDeviationResponse(ClaudeDeviationResponse claudeResponse);
}
