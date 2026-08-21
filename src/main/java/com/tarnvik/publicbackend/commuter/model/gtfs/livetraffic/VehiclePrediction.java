package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import java.time.Duration;
import java.util.List;

/**
 * Everything worked out about one vehicle's running times: how late it is, where that figure came from, and
 * when it is due to leave each stop still ahead of it.
 * <p>
 * The delay used to be a private intermediate — it was folded into the predictions and then dropped — but it
 * is a fact about the network in its own right, and the schematic wants to show it. It is carried out whole
 * rather than recovered later by differencing a prediction against the timetable.
 * <p>
 * {@code delay} and {@code delaySource} are both null when no usable measurement could be made, in which
 * case {@code stopPredictions} is the raw timetable. They are never one without the other.
 */
public record VehiclePrediction(Duration delay, DelaySource delaySource, List<StopPrediction> stopPredictions) {
  /** No delay measurement and no times — for a trip whose stops cannot be read at all. */
  public static VehiclePrediction none() {
    return new VehiclePrediction(null, null, List.of());
  }

  /** Times, but no usable evidence of how late the vehicle is: the predictions are the plain timetable. */
  public static VehiclePrediction uncorrected(List<StopPrediction> stopPredictions) {
    return new VehiclePrediction(null, null, stopPredictions);
  }
}
