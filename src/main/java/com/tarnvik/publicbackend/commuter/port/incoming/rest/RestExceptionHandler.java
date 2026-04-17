package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.service.GtfsDownloadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {
  @ExceptionHandler(GtfsDownloadException.class)
  public ResponseEntity<Map<String, String>> handleGtfsDownloadException(GtfsDownloadException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(Map.of("message", e.getMessage()));
  }
}
