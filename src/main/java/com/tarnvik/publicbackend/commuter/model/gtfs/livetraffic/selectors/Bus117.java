package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GtfsParent;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.AtypicalRouteVariant;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Bus117 extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 20;
  private final static String STATION_ID = "9021001012138000";

  public Bus117() {
    super(STATION_COUNT, STATION_ID);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.BUS, 2);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
    GtfsTripInfo idTrip = findIdTrip(trips);
    return new LiveTrip(idTrip,
      List.of(new AtypicalRouteVariant(new GtfsParent("9021001012281000", "Urban Hjärnes väg"),
        "Nattbuss, passerar inte Bromma Kyrka")));
  }
}
