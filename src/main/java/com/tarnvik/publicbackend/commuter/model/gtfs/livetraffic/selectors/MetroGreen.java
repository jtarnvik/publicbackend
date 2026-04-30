package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;

import java.util.List;

public class MetroGreen extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 28;
  private final static String STATION_ID = "9021001006101000";

  public MetroGreen() {
    super(STATION_COUNT, STATION_ID);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.METRO, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) {
    return null;
//    throw new NotImplementedException();
  }
}
