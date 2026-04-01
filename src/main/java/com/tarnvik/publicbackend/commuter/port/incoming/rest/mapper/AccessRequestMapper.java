package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AccessRequest;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AccessRequestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateConverter.class)
public interface AccessRequestMapper {
  @Mapping(target = "createDate", source = "createDate", qualifiedBy = DateFormat.class)
  AccessRequestResponse toResponse(AccessRequest accessRequest);
}
