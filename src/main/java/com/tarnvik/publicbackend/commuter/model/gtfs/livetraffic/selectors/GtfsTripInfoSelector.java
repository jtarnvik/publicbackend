package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoFullTripException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveForkStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.RouteForkVariant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class GtfsTripInfoSelector {
  public record ForkPart(ParentStopIdentifier start, ParentStopIdentifier end, int length) {}

  private final int stationCount;
  private final ParentStopIdentifier stopIdentifier;

  public abstract LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException;

  private boolean startsWith(GtfsTripInfo trip) {
    GtfsStopInfo firstStop = trip.getStopTimes().getFirst().getStop();
    Optional<GtfsStopInfo> safeParent = GtfsUtil.getSafeParent(firstStop);
    return safeParent.isPresent() && safeParent.get().getStopId().equals(stopIdentifier.getId());
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

  protected RouteForkVariant getRouteForkVariant(List<GtfsTripInfo> trips, ForkPart forkPart) throws GtfsLiveException {
    for (GtfsTripInfo trip : trips) {
      List<GtfsStopTimeInfo> stopTimes = trip.getStopTimes();
      for (int i = 0; i < stopTimes.size(); i++) {
        Optional<GtfsStopInfo> startParent = GtfsUtil.getSafeParent(stopTimes.get(i).getStop());
        if (startParent.isEmpty() || !startParent.get().getStopId().equals(forkPart.start().getId())) {
          continue;
        }
        int endIdx = i + forkPart.length() - 1;
        if (endIdx >= stopTimes.size()) {
          continue;
        }
        Optional<GtfsStopInfo> endParent = GtfsUtil.getSafeParent(stopTimes.get(endIdx).getStop());
        if (endParent.isEmpty() || !endParent.get().getStopId().equals(forkPart.end().getId())) {
          continue;
        }
        List<LiveForkStop> forkStops = new ArrayList<>();
        for (int j = i; j <= endIdx; j++) {
          forkStops.add(new LiveForkStop(stopTimes.get(j)));
        }
        return new RouteForkVariant(forkStops);
      }
    }
    throw new GtfsNoFullTripException();
  }
}
