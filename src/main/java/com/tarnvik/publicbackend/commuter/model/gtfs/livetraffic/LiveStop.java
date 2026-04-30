package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GeoPosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoStopInfoException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(onlyExplicitlyIncluded = true)
public class LiveStop implements GeoPosition {
  @ToString.Include
  private final String stopId;
  @ToString.Include
  private final String stopName;
  @ToString.Include
  private final Double shapeDistTraveled;
  @ToString.Include
  private final Double shapeDistTraveledSinceLast;
  private final double stopLat;
  private final double stopLon;

  public LiveStop(GtfsStopTimeInfo sti, Double distSoFar) throws GtfsLiveException {
    this.shapeDistTraveled = sti.getShapeDistTraveled();
    this.shapeDistTraveledSinceLast = sti.getShapeDistTraveled() - distSoFar;

    GtfsStopInfo posSrc = sti.getStop();
    if (posSrc == null) {
      throw new GtfsNoStopInfoException();
    }
    GtfsStopInfo parent = GtfsUtil.getParent(posSrc);
    this.stopId = parent.getStopId();
    this.stopName = parent.getStopName();
    this.stopLat = parent.getStopLat();
    this.stopLon = parent.getStopLon();
  }

  @Override
  public double getLat() {
    return stopLat;
  }

  @Override
  public double getLng() {
    return stopLon;
  }
}
