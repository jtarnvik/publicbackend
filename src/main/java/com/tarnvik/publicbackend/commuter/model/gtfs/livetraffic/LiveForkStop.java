package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GeoPosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoParentForStopException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoStopInfoException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(onlyExplicitlyIncluded = true)
public class LiveForkStop implements GeoPosition {
  @ToString.Include
  private final String stopId;
  @ToString.Include
  private final String stopName;
  private final double stopLat;
  private final double stopLon;

  public LiveForkStop(GtfsStopTimeInfo sti) throws GtfsLiveException {
    GtfsStopInfo posSrc = sti.getStop();
    if (posSrc == null) {
      throw new GtfsNoStopInfoException();
    }
    GtfsStopInfo parent = GtfsUtil.getParent(posSrc);
    if (parent == null) {
      throw new GtfsNoParentForStopException(posSrc);
    }
    this.stopId = parent.getStopId();
    this.stopName = parent.getStopName();
    this.stopLat = parent.getStopLat();
    this.stopLon = parent.getStopLon();
  }

  LiveForkStop(LiveForkStop source) {
    this.stopId = source.stopId;
    this.stopName = source.stopName;
    this.stopLat = source.stopLat;
    this.stopLon = source.stopLon;
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
