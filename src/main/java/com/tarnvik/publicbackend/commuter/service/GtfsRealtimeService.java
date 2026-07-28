package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GtfsRealtimeService {
  private final SamtrafikenProvider samtrafikenProvider;
  private final GtfsAccessService gtfsAccessService; // will be used when POC expands
  private final GtfsRealtimeCache gtfsCache = new GtfsRealtimeCache();

  public GtfsRealtimeService(SamtrafikenProvider samtrafikenProvider, GtfsAccessService gtfsAccessService) {
    this.samtrafikenProvider = samtrafikenProvider;
    this.gtfsAccessService = gtfsAccessService;
  }

  public RouteDataResponse getRouteData(TransportMode transportMode, int routeGroup, boolean focused) {
    try {
      final GtfsDataset dataset = gtfsAccessService.getDataset();
      List<GtfsVehiclePosition> gtfsVehiclePositions = samtrafikenProvider.fetchVehiclePositions();
      log.info("Total number of vehicles {}", gtfsVehiclePositions.size());

      List<GtfsVehiclePosition> monitoredRouteVP = new ArrayList<>();
      gtfsVehiclePositions.forEach(vp -> {
        Optional<GtfsTripInfo> tripByTripId = dataset.findTripByTripId(vp.getTripId(), transportMode, routeGroup);
        if (tripByTripId.isPresent()) {
          monitoredRouteVP.add(vp);
        }
      });
      log.info("Total number of monitored VP {}", monitoredRouteVP.size());

      return RouteDataResponse.builder().status("OK").build();
    } catch (Exception e) {
      return RouteDataResponse.builder().status("Failure: " + e.getMessage()).build();
    }
  }

  public void poc() {
    try {
      Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> direct = gtfsCache.getContinously();
      if (direct.isEmpty()) {
        log.info("No static data, try again tomorrow.");
        return;
      }
      Map<GtfsRouteInfo, List<GtfsVehiclePosition>> vpByRoute = direct.get();
      final GtfsDataset dataset = gtfsAccessService.getDataset();

      vpByRoute.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(GtfsRouteInfo::getRouteShortName)))
        .forEach(e -> log.info("  line {} -> {} vehicles", e.getKey().getRouteShortName(), e.getValue().size()));

      Optional<GtfsRouteInfo> rt117Opt = vpByRoute.keySet().stream()
        .filter(k -> k.getRouteShortName().equals("117"))
        .findFirst();
      rt117Opt.ifPresent(rt117 -> {
        List<GtfsVehiclePosition> vps117 = vpByRoute.get(rt117Opt.get());
        log.info("Found 117 line, {} vehicles", vps117.size());
        vps117.forEach(vp -> {
          GtfsTripInfo gtfsTripInfo = dataset.findTripByTripId(vp.getTripId()).orElseThrow();

          log.info("117 Vehicle: routeid: {}, tripid: {}, serviceId: {}, direction: {}",
            gtfsTripInfo.getRouteInfo().getRouteId(),
            gtfsTripInfo.getTripId(),
            gtfsTripInfo.getServiceId(),
            gtfsTripInfo.getDirectionId());

          List<GtfsStopTimeInfo> gtfsStopTimes = dataset.findStopTimesByTripId(vp.getTripId()).orElseThrow();
          log.info("Found {} stop times", gtfsStopTimes.size());
          StringBuffer buf = new StringBuffer("Stop chain mot ");
          buf.append(gtfsStopTimes.get(0).getStopHeadsign());
          buf.append(": ");
          String chain = gtfsStopTimes.stream()
            .map(st -> {
              GtfsStopInfo stop = st.getStop();
              GtfsStopInfo parent = stop.getParentStation();
              return stop.getStopName() + "/" + stop.getStopId() + "/" + parent.getStopId() + "/" + parent.getStopName();
            })
            .collect(Collectors.joining(" -> "));
          buf.append(chain);
          log.info(buf.toString());

          List<GtfsStopInfo> gtfsStops = gtfsStopTimes.stream()
            .map(GtfsStopTimeInfo::getStop)
            .toList();

          VehicleLocation vehicleLocation = GtfsGeometryUtil.locateOnRoute(gtfsStops, vp);
          log.info("Postition: {}", vehicleLocation);
        });
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Short-lived cache in front of the Samtrafiken realtime feed.
   * <p>
   * The feed is rate limited (2 000 000 calls per rolling 30 day window), so every client must not trigger
   * its own upstream call. {@link #getDirect()} always goes upstream; {@link #getContinously()} is the one
   * callers should use — it returns the most recently polled value and keeps a background poll loop alive
   * while requests keep arriving.
   * <p>
   * The loop is deliberately request-driven: nothing polls when nobody is watching the live traffic view.
   * The window is sliding — every {@code getContinously()} call pushes the deadline out by
   * {@link #ACTIVE_WINDOW}, so the loop stops that long after the <em>last</em> request rather than a fixed
   * time after it started. A long viewing session therefore never hits a mid-session blocking fetch, and
   * polling stops promptly once the user leaves the view.
   * <p>
   * The interval is a plain sleep <em>between</em> cycles, not a fixed-rate schedule: a slow upstream call
   * pushes the next call later instead of letting calls queue up. The actual spacing achieved is logged
   * every cycle so it can be analysed against the quota.
   */
  private class GtfsRealtimeCache {
    /**
     * Time to sleep between two upstream calls while the loop is active. Measured from the end of one cycle
     * to the start of the next, so the real spacing is this plus the call duration.
     * <p>
     * The API quota (2 000 000 calls per rolling 30 days ≈ 66 700/day) is not the binding constraint here:
     * 5 seconds is 12 calls/minute, so even a loop running around the clock uses about a quarter of it.
     * Render's 0.1 CPU allocation is the more real limit.
     */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    /** How long the loop stays alive after the most recent {@code getContinously()} call. */
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);

    private final AtomicBoolean loopActive = new AtomicBoolean(false);
    private volatile Instant windowEnd = Instant.EPOCH;
    private volatile Map<GtfsRouteInfo, List<GtfsVehiclePosition>> lastResult;

    public Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> getContinously() {
      windowEnd = Instant.now().plus(ACTIVE_WINDOW);

      Map<GtfsRouteInfo, List<GtfsVehiclePosition>> cached = lastResult;
      if (loopActive.get() && cached != null) {
        log.debug("GTFS-RT cache hit — poll loop active, returning cached value ({} routes)", cached.size());
        return Optional.of(cached);
      }

      log.info("GTFS-RT cache cold — fetching directly");
      Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> direct = getDirect();
      direct.ifPresent(result -> {
        lastResult = result;
        startLoop();
      });
      return direct;
    }

    /**
     * Starts the poll loop unless one is already running. The CAS is what makes two simultaneous cold
     * requests start only one loop — they may both have done a direct fetch, which is wasteful but correct.
     */
    private void startLoop() {
      if (!loopActive.compareAndSet(false, true)) {
        log.debug("GTFS-RT poll loop already running");
        return;
      }
      Thread.ofVirtual().name("gtfs-rt-poll").start(this::pollLoop);
    }

    private void pollLoop() {
      log.info("GTFS-RT poll loop started — interval={}s, window={}s",
        POLL_INTERVAL.toSeconds(), ACTIVE_WINDOW.toSeconds());
      long loopStart = System.currentTimeMillis();
      long previousCycleStart = 0;
      int cycle = 0;
      try {
        while (Instant.now().isBefore(windowEnd)) {
          Thread.sleep(POLL_INTERVAL);
          if (!Instant.now().isBefore(windowEnd)) {
            break;
          }
          cycle++;
          long cycleStart = System.currentTimeMillis();
          long sincePreviousCycle = previousCycleStart == 0 ? 0 : cycleStart - previousCycleStart;
          previousCycleStart = cycleStart;
          try {
            getDirect().ifPresent(result -> lastResult = result);
          } catch (Exception e) {
            log.warn("GTFS-RT poll cycle {} failed — keeping previous value: {}", cycle, e.getMessage());
          }
          log.info("GTFS-RT poll cycle {} — cycle={}ms, sincePreviousCycle={}ms, windowRemaining={}s",
            cycle,
            System.currentTimeMillis() - cycleStart,
            sincePreviousCycle,
            Duration.between(Instant.now(), windowEnd).toSeconds());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("GTFS-RT poll loop interrupted after {} cycles", cycle);
      } finally {
        loopActive.set(false);
        log.info("GTFS-RT poll loop stopped — {} cycles over {}s",
          cycle, (System.currentTimeMillis() - loopStart) / 1000);
      }
    }

    public Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> getDirect() {
      try {
        final GtfsDataset dataset = gtfsAccessService.getDataset();
        if (dataset.isEmpty()) {
          log.info("No static data, try again later!");
          return Optional.empty();
        }

        long fetchStart = System.currentTimeMillis();
        List<GtfsVehiclePosition> gtfsVehiclePositions = samtrafikenProvider.fetchVehiclePositions();
        long fetchMs = System.currentTimeMillis() - fetchStart;

        // Single pass: the trip lookup that decides whether a vehicle is on a monitored line also yields the
        // route to group it under, so there is no reason to look it up again afterwards.
        long joinStart = System.currentTimeMillis();
        Map<GtfsRouteInfo, List<GtfsVehiclePosition>> vpByRoute = new HashMap<>();
        int monitoredCount = 0;
        for (GtfsVehiclePosition vp : gtfsVehiclePositions) {
          Optional<GtfsTripInfo> trip = dataset.findTripByTripId(vp.getTripId());
          if (trip.isEmpty()) {
            continue;
          }
          vpByRoute.computeIfAbsent(trip.get().getRouteInfo(), routeInfo -> new ArrayList<>()).add(vp);
          monitoredCount++;
        }
        long joinMs = System.currentTimeMillis() - joinStart;

        log.info("GTFS-RT direct — {} vehicles in feed, {} on monitored lines, {} routes; fetch={}ms, join={}ms, total={}ms",
          gtfsVehiclePositions.size(), monitoredCount, vpByRoute.size(), fetchMs, joinMs, fetchMs + joinMs);
        return Optional.of(vpByRoute);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
