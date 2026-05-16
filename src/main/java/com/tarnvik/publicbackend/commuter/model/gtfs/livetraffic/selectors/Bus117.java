package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.AtypicalRouteVariant;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.SPANGA_STATION;
import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.URBAN_HJARNES_VAG;

@Slf4j
public class Bus117 extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 20;

  public Bus117() {
    super(STATION_COUNT, SPANGA_STATION);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.BUS, 2);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
    GtfsTripInfo idTrip = findIdTrip(trips);
    return new LiveTrip(idTrip,
      List.of(new AtypicalRouteVariant(URBAN_HJARNES_VAG, "Nattbuss, passerar inte Bromma Kyrka")));
  }
}
