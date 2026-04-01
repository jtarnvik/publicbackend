package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SettingsResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserSettingsMapper {
  SettingsResponse toResponse(UserSettings userSettings);
}
