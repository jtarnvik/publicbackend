package com.tarnvik.publicbackend.commuter.model.gtfs.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class GtfsLiveException extends Exception {
  protected GtfsLiveException(String message) {
    super(message);
  }
}
