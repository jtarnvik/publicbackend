package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping("/ping")
  public String ping() {
    return "ok";
  }
}