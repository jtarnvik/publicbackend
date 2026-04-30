package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Train43 extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 28;
  private final static String STATION_ID = "9021001006101000";

  public Train43() {
    super(STATION_COUNT, STATION_ID);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.TRAIN, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
    GtfsTripInfo idTrip = findIdTrip(trips);
    return new LiveTrip(idTrip, new ArrayList<>());
  }
}
