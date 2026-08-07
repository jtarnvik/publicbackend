package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.event.RealtimePollingStateChangedEvent;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.ResolveOutcome;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.ResolvedTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.RouteData;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.RouteFocus;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.TripQuery;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import com.tarnvik.publicbackend.commuter.service.util.GtfsPredictionUtil;
import com.tarnvik.publicbackend.commuter.service.util.GtfsTripMatchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GtfsRealtimeService {
  private static final String STATUS_OK = "OK";
  private static final String STATUS_NO_STATIC_DATA = "No static data";
  private static final String STATUS_NO_LIVE_TRIP = "No live trip for group";
  private static final String STATUS_FOCUS_WINDOW_NOT_FOUND = "Focus window not found in chain";

  private final SamtrafikenProvider samtrafikenProvider;
  private final GtfsAccessService gtfsAccessService;
  private final ApplicationEventPublisher eventPublisher;
  private final GtfsRealtimeCache gtfsCache = new GtfsRealtimeCache();

  public GtfsRealtimeService(SamtrafikenProvider samtrafikenProvider, GtfsAccessService gtfsAccessService,
                             ApplicationEventPublisher eventPublisher) {
    this.samtrafikenProvider = samtrafikenProvider;
    this.gtfsAccessService = gtfsAccessService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Whether the realtime poll loop is currently running — i.e. whether anyone is watching the live
   * traffic view. Read by the terminal dashboard; changes are also announced as
   * {@link RealtimePollingStateChangedEvent}.
   */
  public boolean isPollLoopActive() {
    return gtfsCache.isLoopActive();
  }

  /** The stretch of the canonical chain a focused view shows, as indices into that chain. Both inclusive. */
  private record FocusRange(int startIdx, int endIdx) {}

  /**
   * The live picture of one route group: every vehicle currently running on it, placed on its route.
   * <p>
   * Vehicles are always located against the group's <em>full</em> chain — cropping is applied afterwards, so
   * the geometry never depends on which view was asked for. When focused, the chain sent back is a cropped
   * copy and vehicle indices are rebased onto it, leaving the frontend with a self-contained picture where
   * {@code segIdx} indexes exactly what it was given.
   */
  public RouteData getRouteData(TransportMode transportMode, int routeGroup, boolean focused) {
    try {
      final GtfsDataset dataset = gtfsAccessService.getDataset();
      GroupKey groupKey = new GroupKey(transportMode, routeGroup);
      Optional<LiveTrip> maybeLiveTrip = dataset.findLiveTrip(groupKey);
      if (maybeLiveTrip.isEmpty() || maybeLiveTrip.get().getLiveStops().size() < 2) {
        log.error("!!! NO USABLE LIVE TRIP FOR {} !!! Every monitored group must have one — this is a "
          + "configuration failure, not a data gap. Check the selector registered for this group and the "
          + "id trip it looks for (station count and start terminus), and the dataset build log.", groupKey);
        return RouteData.builder().status(STATUS_NO_LIVE_TRIP).vehicles(List.of()).build();
      }
      LiveTrip fullTrip = maybeLiveTrip.get();
      GtfsMonitoredRoute monitoredRoute = dataset.findMonitoredRoute(groupKey).orElse(null);

      FocusRange range = null;
      if (isFocusedView(focused, monitoredRoute) && hasFocusWindow(monitoredRoute)) {
        range = resolveFocusRange(fullTrip, monitoredRoute, groupKey);
        if (range == null) {
          return RouteData.builder().status(STATUS_FOCUS_WINDOW_NOT_FOUND).vehicles(List.of()).build();
        }
      }

      Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> cached = gtfsCache.getContinously();
      if (cached.isEmpty()) {
        return RouteData.builder().status(STATUS_NO_STATIC_DATA).vehicles(List.of()).build();
      }

      // One instant for the whole request, so every vehicle's predictions are measured against the same
      // moment rather than drifting apart across the loop.
      Instant now = Instant.now();
      List<LiveVehicle> located = new ArrayList<>();
      for (Map.Entry<GtfsRouteInfo, List<GtfsVehiclePosition>> entry : cached.get().entrySet()) {
        if (!matchesGroup(entry.getKey(), transportMode, routeGroup)) {
          continue;
        }
        for (GtfsVehiclePosition vp : entry.getValue()) {
          locate(dataset, fullTrip, vp, now).ifPresent(located::add);
        }
      }

      if (range == null) {
        // Trace: one line per client poll, so more frequent than the poll cycle lines above.
        log.trace("Route data for {}: {} vehicles on the full {} stop chain",
          groupKey, located.size(), fullTrip.getLiveStops().size());
        return RouteData.builder().status(STATUS_OK).liveTrip(fullTrip).vehicles(located).build();
      }
      return buildFocused(groupKey, fullTrip, located, range);
    } catch (Exception e) {
      log.warn("Route data for {}/{} failed: {}", transportMode, routeGroup, e.getMessage(), e);
      return RouteData.builder().status("Failure: " + e.getMessage()).vehicles(List.of()).build();
    }
  }

  /**
   * Finds the live vehicle behind one departure row from SL's departures API.
   * <p>
   * The search runs over the vehicles currently reporting on the requested group — a few dozen at most —
   * rather than over the timetable, which holds thousands of trips per group across every service day. That
   * is both the cheaper search and the more honest one: a trip that matches perfectly but has no vehicle
   * behind it cannot be pointed at on the schematic, so it is not an answer.
   * <p>
   * Resolving deliberately goes through {@link GtfsRealtimeCache#getContinously()} and so renews the poll
   * window. The caller is on their way into the live traffic view, so warming the loop here means their
   * first route data request is a cache hit rather than a blocking upstream fetch.
   */
  public ResolvedTrip resolveTrip(TripQuery query) {
    try {
      final GtfsDataset dataset = gtfsAccessService.getDataset();
      if (dataset.isEmpty()) {
        return ResolvedTrip.of(ResolveOutcome.NO_LIVE_DATA);
      }
      Optional<String> parentStationId = resolveParentStationId(dataset, query);
      if (parentStationId.isEmpty()) {
        log.debug("No parent station for stop area {} ({}) — cannot resolve", query.getStopAreaId(),
          query.getStopAreaName());
        return ResolvedTrip.of(ResolveOutcome.NO_MATCH);
      }
      Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> cached = gtfsCache.getContinously();
      if (cached.isEmpty()) {
        return ResolvedTrip.of(ResolveOutcome.NO_LIVE_DATA);
      }
      return findMatch(dataset, cached.get(), query, parentStationId.get());
    } catch (Exception e) {
      log.warn("Resolving a trip for {}/{} failed: {}", query.getTransportMode(), query.getRouteGroup(),
        e.getMessage(), e);
      return ResolvedTrip.of(ResolveOutcome.NO_LIVE_DATA);
    }
  }

  /**
   * The query's stop as a GTFS parent station id. The id is derived arithmetically from SL's
   * {@code stop_area.id} and only falls back to the stop name when that derivation lands on nothing — see
   * {@link GtfsTripMatchUtil} for why the derivation works and why it is not simply trusted.
   */
  private Optional<String> resolveParentStationId(GtfsDataset dataset, TripQuery query) {
    String derived = GtfsTripMatchUtil.toParentStationId(query.getStopAreaId());
    if (dataset.getStopByStopId(derived).isPresent()) {
      return Optional.of(derived);
    }
    log.debug("Derived parent station {} is not in the dataset — falling back to the name {}", derived,
      query.getStopAreaName());
    return dataset.findParentStationByName(query.getStopAreaName()).map(GtfsStopInfo::getStopId);
  }

  private ResolvedTrip findMatch(GtfsDataset dataset, Map<GtfsRouteInfo, List<GtfsVehiclePosition>> byRoute,
                                 TripQuery query, String parentStationId) {
    List<GtfsVehiclePosition> matches = new ArrayList<>();
    for (Map.Entry<GtfsRouteInfo, List<GtfsVehiclePosition>> entry : byRoute.entrySet()) {
      if (!matchesGroup(entry.getKey(), query.getTransportMode(), query.getRouteGroup())) {
        continue;
      }
      for (GtfsVehiclePosition vp : entry.getValue()) {
        Optional<GtfsTripInfo> trip = dataset.findTripByTripId(vp.getTripId());
        if (trip.isPresent() && GtfsTripMatchUtil.matches(trip.get(), query, parentStationId, observedAt(vp))) {
          matches.add(vp);
        }
      }
    }
    if (matches.isEmpty()) {
      log.debug("No live vehicle matches {} {} from {} at {} — the vehicle is most likely not reporting yet",
        query.getTransportMode(), query.getLine(), parentStationId, query.getScheduled());
      return ResolvedTrip.of(ResolveOutcome.NO_MATCH);
    }
    if (matches.size() > 1) {
      log.warn("{} live vehicles match {} {} from {} at {} — the content key should be unique, so this is "
          + "worth looking at", matches.size(), query.getTransportMode(), query.getLine(), parentStationId,
        query.getScheduled());
      return ResolvedTrip.of(ResolveOutcome.AMBIGUOUS);
    }
    GtfsVehiclePosition match = matches.getFirst();
    log.debug("Resolved {} {} from {} at {} to trip {}", query.getTransportMode(), query.getLine(),
      parentStationId, query.getScheduled(), match.getTripId());
    return ResolvedTrip.matched(match.getTripId(), match.getVehicleId());
  }

  /** When the vehicle reported itself, which is what places its trip on a service day. */
  private Instant observedAt(GtfsVehiclePosition vp) {
    return vp.getTimestamp() > 0 ? Instant.ofEpochSecond(vp.getTimestamp()) : Instant.now();
  }

  /**
   * A group marked {@code onlyFocused} is served focused whatever the caller asked for. Unfocused it would
   * have to draw a fork the schematic cannot render, and vehicles from branches it does not draw would be
   * placed at stations they never reach. The frontend disables the toggle; this makes it stick.
   */
  private boolean isFocusedView(boolean focused, GtfsMonitoredRoute monitoredRoute) {
    if (monitoredRoute != null && monitoredRoute.isOnlyFocused() && !focused) {
      log.debug("Forcing focused view — group is marked onlyFocused");
      return true;
    }
    return focused;
  }

  private boolean hasFocusWindow(GtfsMonitoredRoute monitoredRoute) {
    return monitoredRoute != null
      && monitoredRoute.getFocusStart() != null
      && monitoredRoute.getFocusEnd() != null;
  }

  /**
   * Locates the configured focus window in the chain. Null — and a loud error — when either end cannot be
   * found or they are the wrong way round: the ids are free text with no reference to the chain, so a typo
   * or a stop that fell out of the timetable surfaces only here.
   */
  private FocusRange resolveFocusRange(LiveTrip trip, GtfsMonitoredRoute monitoredRoute, GroupKey groupKey) {
    int startIdx = indexOfStop(trip, monitoredRoute.getFocusStart());
    int endIdx = indexOfStop(trip, monitoredRoute.getFocusEnd());
    if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx) {
      log.error("!!! UNUSABLE FOCUS WINDOW FOR {} !!! focusStart={} resolved to index {}, focusEnd={} to {}. "
        + "Both must be parent station ids present in the chain, in chain order. Check gtfs_monitored_route.",
        groupKey, monitoredRoute.getFocusStart(), startIdx, monitoredRoute.getFocusEnd(), endIdx);
      return null;
    }
    return new FocusRange(startIdx, endIdx);
  }

  private int indexOfStop(LiveTrip trip, String stopId) {
    List<LiveStop> stops = trip.getLiveStops();
    for (int i = 0; i < stops.size(); i++) {
      if (stops.get(i).getStopId().equals(stopId)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Crops the chain to the window, rebases the vehicles inside it, and counts those approaching from beyond
   * each end. A vehicle that has already left the window is dropped without being counted — it tells the
   * viewer nothing about when the next one arrives.
   */
  private RouteData buildFocused(GroupKey groupKey, LiveTrip fullTrip, List<LiveVehicle> located,
                                 FocusRange range) {
    List<LiveStop> fullStops = fullTrip.getLiveStops();
    LiveTrip croppedTrip = new LiveTrip(
      fullTrip.getDirection(),
      fullTrip.getStopHeading(),
      new ArrayList<>(fullStops.subList(range.startIdx(), range.endIdx() + 1)),
      fullTrip.getRouteVariants());

    Set<String> croppedStopIds = croppedTrip.getLiveStops().stream()
      .map(LiveStop::getStopId)
      .collect(Collectors.toSet());

    List<LiveVehicle> inside = new ArrayList<>();
    int approachingAtStart = 0;
    int approachingAtEnd = 0;
    for (LiveVehicle vehicle : located) {
      int segIdx = vehicle.getLocation().segIdx();
      boolean headingDown = vehicle.getTrip().getDirectionId() == fullTrip.getDirection();
      if (segIdx >= range.startIdx() && segIdx < range.endIdx()) {
        inside.add(rebase(vehicle, range.startIdx(), croppedStopIds));
      } else if (segIdx < range.startIdx()) {
        if (headingDown) {
          approachingAtStart++;
        }
      } else if (!headingDown) {
        approachingAtEnd++;
      }
    }

    RouteFocus focus = RouteFocus.builder()
      .truncatedStart(range.startIdx() > 0)
      .truncatedEnd(range.endIdx() < fullStops.size() - 1)
      .approachingAtStart(approachingAtStart)
      .approachingAtEnd(approachingAtEnd)
      .build();

    // Trace: one line per client poll. This is where to look first when the focus crop looks wrong.
    log.trace("Route data for {} focused to stops {}-{} of {}: {} vehicles inside, {} approaching at start, "
        + "{} approaching at end",
      groupKey, range.startIdx(), range.endIdx(), fullStops.size(), inside.size(),
      approachingAtStart, approachingAtEnd);
    return RouteData.builder()
      .status(STATUS_OK)
      .liveTrip(croppedTrip)
      .vehicles(inside)
      .focus(focus)
      .build();
  }

  /**
   * Shifts a vehicle's segment index from the full chain onto the cropped one, and drops the predictions
   * for stops the cropped chain does not draw — they would be times against rows that are not there.
   */
  private LiveVehicle rebase(LiveVehicle vehicle, int startIdx, Set<String> croppedStopIds) {
    VehicleLocation location = vehicle.getLocation();
    return LiveVehicle.builder()
      .position(vehicle.getPosition())
      .location(new VehicleLocation(location.segIdx() - startIdx, location.t(), location.dist()))
      .trip(vehicle.getTrip())
      .stopPredictions(vehicle.getStopPredictions().stream()
        .filter(prediction -> croppedStopIds.contains(prediction.stopId()))
        .toList())
      .build();
  }

  private boolean matchesGroup(GtfsRouteInfo routeInfo, TransportMode transportMode, int routeGroup) {
    GtfsMonitoredRoute monitoredRoute = routeInfo.getMonitoredRoute();
    return monitoredRoute.getTransportMode() == transportMode && monitoredRoute.getRouteGroup() == routeGroup;
  }

  /**
   * Places a vehicle on the stop chain of the trip it is running. Empty when the trip is unknown, or when it
   * has fewer than two stops — a single stop has no segment to project onto.
   */
  private Optional<LiveVehicle> locate(GtfsDataset dataset, LiveTrip liveTrip, GtfsVehiclePosition vp,
                                       Instant now) {
    Optional<GtfsTripInfo> maybeTrip = dataset.findTripByTripId(vp.getTripId());
    if (maybeTrip.isEmpty()) {
      return Optional.empty();
    }
    VehicleLocation location = GtfsGeometryUtil.locateOnRoute(liveTrip.getLiveStops(), vp);
    LiveVehicle vehicle = LiveVehicle.builder()
      .position(vp)
      .location(location)
      .trip(maybeTrip.get())
      .build();
    // Predicted against the full chain for the same reason the geometry is: what the caller asked to see
    // must not change where anything is, only how much of it is sent.
    return Optional.of(vehicle.toBuilder()
      .stopPredictions(GtfsPredictionUtil.predict(liveTrip, vehicle, now))
      .build());
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
      publishState(true);
      Thread.ofVirtual().name("gtfs-rt-poll").start(this::pollLoop);
    }

    public boolean isLoopActive() {
      return loopActive.get();
    }

    /**
     * Announces a loop transition. Wrapped because listeners run synchronously on the caller's
     * thread: a listener that throws must not be able to abort the poll loop or leave
     * {@code loopActive} disagreeing with reality.
     */
    private void publishState(boolean active) {
      try {
        eventPublisher.publishEvent(new RealtimePollingStateChangedEvent(active));
      } catch (Exception e) {
        log.warn("Listener failed for realtime polling state change (active={}): {}", active, e.getMessage(), e);
      }
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
          // Trace: one line per cycle. sincePreviousCycle is how the real interval drift was measured.
          log.trace("GTFS-RT poll cycle {} — cycle={}ms, sincePreviousCycle={}ms, windowRemaining={}s",
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
        publishState(false);
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

        // Trace: one line per poll cycle. Kept because it is the only place the fetch/join split is
        // measured — raise it to info when the timings need looking at again.
        log.trace("GTFS-RT direct — {} vehicles in feed, {} on monitored lines, {} routes; fetch={}ms, join={}ms, total={}ms",
          gtfsVehiclePositions.size(), monitoredCount, vpByRoute.size(), fetchMs, joinMs, fetchMs + joinMs);
        return Optional.of(vpByRoute);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
