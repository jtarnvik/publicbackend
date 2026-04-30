package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors;

import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoRegisteredSelectorForGroupKeyException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GtfsTripInfoSelectorFactory {
  private static Map<GroupKey, GtfsTripInfoSelector> selectors = new HashMap<>() ;

  static {
    selectors.put(Bus112.getGroupKey(), new Bus112());
    selectors.put(Bus117.getGroupKey(), new Bus117());
    selectors.put(MetroGreen.getGroupKey(), new MetroGreen());
    selectors.put(Train43.getGroupKey(), new Train43());
  }

  public static GtfsTripInfoSelector findMatching(GroupKey groupKey) {
    GtfsTripInfoSelector selector = selectors.get(groupKey);
    if (selector == null) {
      throw new GtfsNoRegisteredSelectorForGroupKeyException(groupKey);
    }
    return selector;
  }
}
