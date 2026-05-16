package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoFullTripException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;

import java.util.List;

import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.RANDOW;

public class MetroGreen extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 28;

  public MetroGreen() {
    super(STATION_COUNT, RANDOW);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.METRO, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsNoFullTripException {
//    GtfsTripInfo idTrip = findIdTrip(trips);
    return null;
//    throw new NotImplementedException();
  }
}
