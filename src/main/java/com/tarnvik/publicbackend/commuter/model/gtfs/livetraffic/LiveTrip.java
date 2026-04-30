package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsEmptyTripException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.EndStopRouteVariant;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.RouteVariant;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class LiveTrip {
  private int direction;
  private String stopHeading;
  private final List<LiveStop> liveStops;
  private final Map<Integer, RouteVariant> edgeVariants = new HashMap<>();
  private final List<RouteVariant> routeVariants;

  public LiveTrip(GtfsTripInfo idTrip, List<RouteVariant> variants) throws GtfsLiveException {
    if (idTrip.getStopTimes().isEmpty()) {
      throw new GtfsEmptyTripException();
    }
    this.stopHeading = idTrip.getStopTimes().getFirst().getStopHeadsign();
    this.direction = idTrip.getDirectionId();
    edgeVariants.put(direction, new EndStopRouteVariant(idTrip.getStopTimes().getLast().getStop().getParent()));
    edgeVariants.put(GtfsUtil.getReverseDirection(direction), new EndStopRouteVariant(idTrip.getStopTimes().getFirst().getStop().getParent()));
    this.routeVariants = variants;

    Double distSoFar = 0.0;
    this.liveStops = new ArrayList<>();
    List<GtfsStopTimeInfo> stopTimes = idTrip.getStopTimes();
    for (GtfsStopTimeInfo sti : stopTimes) {
      LiveStop liveStop = new LiveStop(sti, distSoFar);
      distSoFar = liveStop.getShapeDistTraveled();
      this.liveStops.add(liveStop);
    }
  }
}
