package com.tarnvik.publicbackend.commuter.exception;

public class GtfsDownloadException extends RuntimeException {
  public GtfsDownloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
