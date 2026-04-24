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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThat(bus1.getTransportMode()).isEqualTo("BUS");
    assertThat(bus1.getRouteGroup()).isEqualTo(1);
    assertThat(bus1.getDisplayName()).isEqualTo("112");

    MonitoredRouteGroupResponse bus2 = result.get(1);
    assertThat(bus2.getTransportMode()).isEqualTo("BUS");
    assertThat(bus2.getRouteGroup()).isEqualTo(2);
    assertThat(bus2.getDisplayName()).isEqualTo("117");

    MonitoredRouteGroupResponse metro1 = result.get(2);
    assertThat(metro1.getTransportMode()).isEqualTo("METRO");
    assertThat(metro1.getRouteGroup()).isEqualTo(1);
    // Names must be joined in numeric order (17, 18, 19) regardless of insertion order
    assertThat(metro1.getDisplayName()).isEqualTo("17/18/19");

    MonitoredRouteGroupResponse train1 = result.get(3);
    assertThat(train1.getTransportMode()).isEqualTo("TRAIN");
    assertThat(train1.getRouteGroup()).isEqualTo(1);
    assertThat(train1.getDisplayName()).isEqualTo("43/44");
    assertThat(train1.getFocusStart()).isNull();
    assertThat(train1.getFocusEnd()).isNull();
    assertThat(train1.isOnlyFocused()).isFalse();
  }

  @Test
  void getMonitoredRouteGroups_includesFocusFieldsFromRepresentativeRoute() {
    GtfsMonitoredRoute r43 = route("43", TransportMode.TRAIN, 1);
    r43.setFocusStart("9021001001009001");
    r43.setFocusEnd("9021001001007001");
    r43.setOnlyFocused(true);
    GtfsMonitoredRoute r44 = route("44", TransportMode.TRAIN, 1);
    r44.setFocusStart("9021001001009001");
    r44.setFocusEnd("9021001001007001");
    r44.setOnlyFocused(true);

    GtfsDataset dataset = new GtfsDataset(
      List.of(r43, r44),
      Collections.emptyMap(), Collections.emptyMap(),
      Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()
    );
    ReflectionTestUtils.setField(service, "dataset", new AtomicReference<>(dataset));

    List<MonitoredRouteGroupResponse> result = service.getMonitoredRouteGroups();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFocusStart()).isEqualTo("9021001001009001");
    assertThat(result.get(0).getFocusEnd()).isEqualTo("9021001001007001");
    assertThat(result.get(0).isOnlyFocused()).isTrue();
  }

  @Test
  void validateRouteGroupConsistency_consistentGroup_doesNotThrow() {
    GtfsMonitoredRoute r43 = route("43", TransportMode.TRAIN, 1);
    r43.setFocusStart("start-id");
    r43.setFocusEnd("end-id");
    r43.setOnlyFocused(true);
    GtfsMonitoredRoute r44 = route("44", TransportMode.TRAIN, 1);
    r44.setFocusStart("start-id");
    r44.setFocusEnd("end-id");
    r44.setOnlyFocused(true);

    service.validateRouteGroupConsistency(List.of(r43, r44));
  }

  @Test
  void validateRouteGroupConsistency_inconsistentOnlyFocused_throws() {
    GtfsMonitoredRoute r43 = route("43", TransportMode.TRAIN, 1);
    GtfsMonitoredRoute r44 = route("44", TransportMode.TRAIN, 1);
    r44.setOnlyFocused(true);

    assertThatThrownBy(() -> service.validateRouteGroupConsistency(List.of(r43, r44)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("inconsistent");
  }

  @Test
  void validateRouteGroupConsistency_inconsistentFocusStart_throws() {
    GtfsMonitoredRoute r43 = route("43", TransportMode.TRAIN, 1);
    r43.setFocusStart("start-a");
    GtfsMonitoredRoute r44 = route("44", TransportMode.TRAIN, 1);
    r44.setFocusStart("start-b");

    assertThatThrownBy(() -> service.validateRouteGroupConsistency(List.of(r43, r44)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("inconsistent");
  }

  @Test
  void validateRouteGroupConsistency_inconsistentFocusEnd_throws() {
    GtfsMonitoredRoute r43 = route("43", TransportMode.TRAIN, 1);
    r43.setFocusEnd("end-a");
    GtfsMonitoredRoute r44 = route("44", TransportMode.TRAIN, 1);
    r44.setFocusEnd("end-b");

    assertThatThrownBy(() -> service.validateRouteGroupConsistency(List.of(r43, r44)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("inconsistent");
  }

  private static GtfsMonitoredRoute route(String shortName, TransportMode mode, int group) {
    GtfsMonitoredRoute r = new GtfsMonitoredRoute();
    r.setRouteShortName(shortName);
    r.setTransportMode(mode);
    r.setRouteGroup(group);
    return r;
  }
}
