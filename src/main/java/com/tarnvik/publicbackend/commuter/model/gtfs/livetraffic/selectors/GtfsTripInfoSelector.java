package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoFullTripException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class GtfsTripInfoSelector {
  private final int stationCount;
  private final String stationId;

  public abstract LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException;

  private boolean startsWith(GtfsTripInfo trip) {
    GtfsStopInfo firstStop = trip.getStopTimes().getFirst().getStop();
    Optional<GtfsStopInfo> safeParent = GtfsUtil.getSafeParent(firstStop);
    return safeParent.isPresent() && safeParent.get().getStopId().equals(stationId);
  }

  private boolean hasCorrectNumberOfStops(GtfsTripInfo trip) {
    return trip.getStopTimes().size() == stationCount;
  }

  protected boolean isIdTrip(GtfsTripInfo trip) {
    return hasCorrectNumberOfStops(trip) && startsWith(trip);
  }

  protected GtfsTripInfo findIdTrip(List<GtfsTripInfo> trips) throws GtfsNoFullTripException {
    GtfsTripInfo idTrip = null;
    for (GtfsTripInfo trip : trips) {
      if (isIdTrip(trip)) {
        idTrip = trip;
        log.info("Stops: {}, start: {}/{}",
          trip.getStopTimes().size(),
          trip.getStopTimes().getFirst().getStop().getParentStation().getStopName(),
          trip.getStopTimes().getFirst().getStop().getParentStation().getStopId());
        break;
      }
    }
    if (idTrip == null) {
      throw new GtfsNoFullTripException();
    }
    return idTrip;
  }
}
