package com.tarnvik.publicbackend.commuter.model.gtfs.exception;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class GtfsNoRegisteredSelectorForGroupKeyException extends RuntimeException{
  private final GroupKey groupKey;
}
