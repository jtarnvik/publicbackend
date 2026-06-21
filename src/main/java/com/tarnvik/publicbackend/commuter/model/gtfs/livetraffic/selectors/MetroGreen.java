package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations.RouteVariant;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.FARSTA_STRAND;
import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.HASSELBY_STRAND;
import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.SKARMARBRINK;
import static com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier.SKARPNACK;

@Slf4j
public class MetroGreen extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 35;
  private List<ForkPart> forks = List.of(
    new ForkPart(SKARMARBRINK, FARSTA_STRAND, 9),
    new ForkPart(SKARMARBRINK, SKARPNACK, 6)
    );

  public MetroGreen() {
    super(STATION_COUNT, HASSELBY_STRAND);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.METRO, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
    GtfsTripInfo idTrip = findIdTrip(trips);
    List<RouteVariant> variants = new ArrayList<>();
    forks.forEach(fork -> {
      try {
        variants.add(getRouteForkVariant(trips, fork));
      } catch (GtfsLiveException e) {
        log.warn("No fork for specified variant, {}", fork);
      }
    });

    return new LiveTrip(idTrip, variants);
  }
}
