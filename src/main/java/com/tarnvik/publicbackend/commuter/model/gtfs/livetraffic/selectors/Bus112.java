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
public class Bus112 extends GtfsTripInfoSelector {
  private final static int STATION_COUNT = 23;
  private final static String STATION_ID = "9021001012025000";

  public Bus112() {
    super(STATION_COUNT, STATION_ID);
  }

  public static GroupKey getGroupKey() {
    return new GroupKey(TransportMode.BUS, 1);
  }

  @Override
  public LiveTrip select(List<GtfsTripInfo> trips) throws GtfsLiveException {
//    boolean f20 = false;
//    boolean f23 = false;
//
//    for (GtfsTripInfo t : trips) {
//      if (t.getStopTimes().size() == 20 && !f20){
//        f20 = true;
//        log.info("F20 -- ");
//        t.getStopTimes().forEach(sti -> {
//          try {
//            log.info("Stop: "+sti.getStop().getParent().getStopName());
//          } catch (GtfsNoParentForStopException e) {
//            throw new RuntimeException(e);
//          }
//        });
//      }
//      if (t.getStopTimes().size() == 23 && !f23){
//        f23 = true;
//        log.info("F23 -- ");
//        List<GtfsStopTimeInfo> stopTimes = new ArrayList<>(t.getStopTimes());
//        Collections.reverse(stopTimes);
//        stopTimes.stream().forEach(sti -> {
//          try {
//            log.info("Stop: "+sti.getStop().getParent().getStopName());
//          } catch (GtfsNoParentForStopException e) {
//            throw new RuntimeException(e);
//          }
//        });
//      }
//    }
    GtfsTripInfo idTrip = findIdTrip(trips);
//    Fles hållplaster, men den behöver vändas på

    return new LiveTrip(idTrip, new ArrayList<>());
  }
}
