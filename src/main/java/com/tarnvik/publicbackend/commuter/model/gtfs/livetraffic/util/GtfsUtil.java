package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoParentForStopException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsUnknownDirectionId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GtfsUtil {
  public static GtfsStopInfo getParent(GtfsStopInfo stop) throws GtfsNoParentForStopException {
    if (!stop.hasParentStation()) {
      throw new GtfsNoParentForStopException(stop);
    }
    return stop.getParentStation();
  }

  public static Optional<GtfsStopInfo> getSafeParent(GtfsStopInfo stop) {
    if (!stop.hasParentStation()) {
      return Optional.empty();
    }
    return Optional.of(stop.getParentStation());
  }

  public static String getParentId(GtfsStopInfo stop) throws GtfsNoParentForStopException {
    return getParent(stop).getStopId();
  }

  public static int getReverseDirection(int directionId) throws GtfsUnknownDirectionId {
    if (directionId == 0) {
      return 1;
    } else if (directionId == 1) {
      return 0;
    } else {
      throw new GtfsUnknownDirectionId();
    }
  }
}
