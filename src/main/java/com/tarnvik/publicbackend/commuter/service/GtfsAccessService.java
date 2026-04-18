package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class GtfsAccessService {
  private final AtomicReference<GtfsDataset> dataset = new AtomicReference<>(new GtfsDataset());

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready — loading GTFS dataset from database");
    rebuildDataset();
  }

  public void rebuildDataset() {
    log.info("Rebuilding GTFS dataset");
    dataset.set(new GtfsDataset());
  }

  public GtfsDataset getDataset() {
    return dataset.get();
  }
}
