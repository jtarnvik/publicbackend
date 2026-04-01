package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AllowedUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateConverter.class)
public interface AllowedUserMapper {
  @Mapping(target = "createDate", source = "createDate", qualifiedBy = DateFormat.class)
  AllowedUserResponse toResponse(AllowedUser allowedUser);
}
