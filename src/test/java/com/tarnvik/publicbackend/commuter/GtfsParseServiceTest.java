package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsDownloadLogRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDateId;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTime;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeId;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.service.GtfsParseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GtfsParseServiceTest {

  @DynamicPropertySource
  static void gtfsProperties(DynamicPropertyRegistry registry) {
    try {
      URL resource = GtfsParseServiceTest.class.getClassLoader().getResource("gtfs");
      String path = Path.of(resource.toURI()).toString();
      registry.add("gtfs.unzip-dir", () -> path);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Could not resolve test gtfs resource directory", e);
    }
  }

  @Autowired GtfsParseService gtfsParseService;
  @Autowired GtfsDownloadLogRepository gtfsDownloadLogRepository;
  @Autowired GtfsRouteRepository gtfsRouteRepository;
  @Autowired GtfsTripRepository gtfsTripRepository;
  @Autowired GtfsStopTimeRepository gtfsStopTimeRepository;
  @Autowired GtfsStopRepository gtfsStopRepository;
  @Autowired GtfsCalendarDateRepository gtfsCalendarDateRepository;

  @MockitoBean ClaudeProvider claudeProvider;
  @MockitoBean PushoverProvider pushoverProvider;

  @BeforeEach
  void setup() {
    GtfsDownloadLog log = new GtfsDownloadLog();
    log.setDate(LocalDate.now());
    log.setStatus(GtfsDownloadStatus.UNZIP_DONE);
    gtfsDownloadLogRepository.save(log);
  }

  @AfterEach
  void cleanup() {
    gtfsStopTimeRepository.deleteAllInBatch();
    gtfsStopRepository.deleteAllInBatch();
    gtfsCalendarDateRepository.deleteAllInBatch();
    gtfsTripRepository.deleteAllInBatch();
    gtfsRouteRepository.deleteAllInBatch();
    gtfsDownloadLogRepository.deleteAll();
  }

  @Test
  void parseIfReady_filtersToMonitoredRoutesOnly() {
    gtfsParseService.parseIfReady();

    // Routes: 3 retained (43, 43X, 117); route-999 excluded (not monitored); route-other excluded (wrong agency)
    assertThat(gtfsRouteRepository.count()).isEqualTo(3);
    assertThat(gtfsRouteRepository.findAll())
      .extracting(r -> r.getRouteShortName())
      .containsExactlyInAnyOrder("43", "43X", "117");
    assertThat(gtfsRouteRepository.findById("route-999")).isEmpty();
    assertThat(gtfsRouteRepository.findById("route-other")).isEmpty();

    // Trips: 4 retained; trip-999-1 and trip-other-1 excluded
    assertThat(gtfsTripRepository.count()).isEqualTo(4);
    assertThat(gtfsTripRepository.findById("trip-43x-1")).isPresent();
    assertThat(gtfsTripRepository.findById("trip-999-1")).isEmpty();
    assertThat(gtfsTripRepository.findById("trip-other-1")).isEmpty();

    // Stop times: 9 retained (11 in file, 2 excluded); 25:30:00 stored intact as String
    assertThat(gtfsStopTimeRepository.count()).isEqualTo(9);
    GtfsStopTimeId lateTimeId = new GtfsStopTimeId();
    lateTimeId.setTripId("trip-43-1");
    lateTimeId.setStopSequence(3);
    Optional<GtfsStopTime> lateStopTime = gtfsStopTimeRepository.findById(lateTimeId);
    assertThat(lateStopTime).isPresent();
    assertThat(lateStopTime.get().getArrivalTime()).isEqualTo("25:30:00");

    // Stops: 5 retained; stop-F excluded (not referenced by retained trips); parent-1 excluded (same reason)
    assertThat(gtfsStopRepository.count()).isEqualTo(5);
    assertThat(gtfsStopRepository.findById("stop-F")).isEmpty();
    assertThat(gtfsStopRepository.findById("parent-1")).isEmpty();

    // Calendar dates: 4 retained; exception_type=2 excluded; non-monitored service IDs excluded
    assertThat(gtfsCalendarDateRepository.count()).isEqualTo(4);
    GtfsCalendarDateId type2Id = new GtfsCalendarDateId();
    type2Id.setServiceId("svc-A");
    type2Id.setServiceDate(LocalDate.of(2026, 4, 21));
    assertThat(gtfsCalendarDateRepository.findById(type2Id)).isEmpty();
    GtfsCalendarDateId nonMonitoredId = new GtfsCalendarDateId();
    nonMonitoredId.setServiceId("svc-D");
    nonMonitoredId.setServiceDate(LocalDate.of(2026, 4, 19));
    assertThat(gtfsCalendarDateRepository.findById(nonMonitoredId)).isEmpty();

    // Log status updated to PARSE_DONE
    GtfsDownloadLog entry = gtfsDownloadLogRepository.findByDate(LocalDate.now()).orElseThrow();
    assertThat(entry.getStatus()).isEqualTo(GtfsDownloadStatus.PARSE_DONE);
  }
}
