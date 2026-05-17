package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.ALVIK;

@Slf4j
public class Bus112 extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 23;

  public Bus112() {
    super(STATION_COUNT, ALVIK);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.BUS, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
    GtfsTripInfo idTrip = findIdTrip(trips);
    LiveTrip liveTrip = new LiveTrip(idTrip, new ArrayList<>());
    liveTrip.reverseTrip();
    return liveTrip;
  }
}
