package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsMonitoredRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GtfsAccessServiceRouteGroupTest {

  @InjectMocks GtfsAccessService service;

  @Mock GtfsMonitoredRouteRepository gtfsMonitoredRouteRepository;
  @Mock GtfsRouteRepository gtfsRouteRepository;
  @Mock GtfsTripRepository gtfsTripRepository;
  @Mock GtfsStopRepository gtfsStopRepository;
  @Mock GtfsStopTimeRepository gtfsStopTimeRepository;
  @Mock GtfsCalendarDateRepository gtfsCalendarDateRepository;

  @BeforeEach
  void setUp() {
    // Routes deliberately provided out of order to exercise sorting in both dimensions
    List<GtfsMonitoredRoute> routes = List.of(
      route("44", TransportMode.TRAIN, 1),
      route("43", TransportMode.TRAIN, 1),
      route("19", TransportMode.METRO, 1),
      route("17", TransportMode.METRO, 1),
      route("18", TransportMode.METRO, 1),
      route("117", TransportMode.BUS, 2),
      route("112", TransportMode.BUS, 1)
    );

    GtfsDataset dataset = new GtfsDataset(
      routes,
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap()
    );
    ReflectionTestUtils.setField(service, "dataset", new AtomicReference<>(dataset));
  }

  @Test
  void getMonitoredRouteGroups_groupsRoutesAndSortsNumerically() {
    List<MonitoredRouteGroupResponse> result = service.getMonitoredRouteGroups();

    assertThat(result).hasSize(4);

    // Output order: transportMode ascending (BUS < METRO < TRAIN), then routeGroup ascending
    MonitoredRouteGroupResponse bus1 = result.get(0);
    assertThat(bus1.transportMode()).isEqualTo("BUS");
    assertThat(bus1.routeGroup()).isEqualTo(1);
    assertThat(bus1.displayName()).isEqualTo("112");

    MonitoredRouteGroupResponse bus2 = result.get(1);
    assertThat(bus2.transportMode()).isEqualTo("BUS");
    assertThat(bus2.routeGroup()).isEqualTo(2);
    assertThat(bus2.displayName()).isEqualTo("117");

    MonitoredRouteGroupResponse metro1 = result.get(2);
    assertThat(metro1.transportMode()).isEqualTo("METRO");
    assertThat(metro1.routeGroup()).isEqualTo(1);
    // Names must be joined in numeric order (17, 18, 19) regardless of insertion order
    assertThat(metro1.displayName()).isEqualTo("17/18/19");

    MonitoredRouteGroupResponse train1 = result.get(3);
    assertThat(train1.transportMode()).isEqualTo("TRAIN");
    assertThat(train1.routeGroup()).isEqualTo(1);
    assertThat(train1.displayName()).isEqualTo("43/44");
  }

  private static GtfsMonitoredRoute route(String shortName, TransportMode mode, int group) {
    GtfsMonitoredRoute r = new GtfsMonitoredRoute();
    r.setRouteShortName(shortName);
    r.setTransportMode(mode);
    r.setRouteGroup(group);
    return r;
  }
}
