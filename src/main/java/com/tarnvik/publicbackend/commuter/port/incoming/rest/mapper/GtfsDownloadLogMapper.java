package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsStatusResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateConverter.class)
public interface GtfsDownloadLogMapper {
  @Mapping(target = "date", source = "date", qualifiedBy = LocalDateFormat.class)
  @Mapping(target = "status", source = "status")
  @Mapping(target = "downloadStartTime", source = "downloadStartTime", qualifiedBy = DateFormat.class)
  @Mapping(target = "downloadEndTime", source = "downloadEndTime", qualifiedBy = DateFormat.class)
  @Mapping(target = "unzipStartTime", source = "unzipStartTime", qualifiedBy = DateFormat.class)
  @Mapping(target = "unzipEndTime", source = "unzipEndTime", qualifiedBy = DateFormat.class)
  @Mapping(target = "parseStartTime", source = "parseStartTime", qualifiedBy = DateFormat.class)
  @Mapping(target = "parseEndTime", source = "parseEndTime", qualifiedBy = DateFormat.class)
  GtfsStatusResponse toResponse(GtfsDownloadLog log);
}
