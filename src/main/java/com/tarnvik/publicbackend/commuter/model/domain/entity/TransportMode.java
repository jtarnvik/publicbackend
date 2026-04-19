package com.tarnvik.publicbackend.commuter.model.domain.entity;

public enum TransportMode {
  TRAIN(100),
  BUS(700),
  METRO(401);

  private final int gtfsRouteType;

  TransportMode(int gtfsRouteType) {
    this.gtfsRouteType = gtfsRouteType;
  }

  public int getGtfsRouteType() {
    return gtfsRouteType;
  }
}
