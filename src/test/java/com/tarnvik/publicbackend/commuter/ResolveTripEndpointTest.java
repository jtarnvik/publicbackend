package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract checks for {@code GET /api/protected/gtfs/resolve-trip}.
 * <p>
 * The integration profile has no GTFS data — {@code GtfsDownloadJob} is {@code @Profile("!test")} and
 * nothing seeds the tables — so the matching itself is not exercised here; that lives in
 * {@code GtfsTripMatchUtilTest}, which can build trips directly. What is worth pinning down at this level is
 * the shape of the endpoint: that a well-formed request answers 200 with an outcome rather than an error
 * status, and that a malformed one is rejected before it reaches the service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResolveTripEndpointTest {
  private static final String TEST_EMAIL = "jtarnvik@gmail.com";
  private static final String URL = "/api/protected/gtfs/resolve-trip";

  /**
   * Exactly what SL's departures API puts in {@code departure.scheduled} — no offset, no {@code Z}. Spelled
   * out here because an endpoint that took an {@code Instant} would reject every real request with 400, and
   * nothing in a hand-written test would have noticed.
   */
  private static final String SL_SCHEDULED = "2026-08-07T09:52:52";

  @Autowired
  private MockMvc mockMvc;

  // Both reach outside and both need credentials the test profile does not carry — mocked for the same
  // reason every other integration test here mocks them.
  @MockitoBean
  private PushoverProvider pushoverProvider;

  @MockitoBean
  private ClaudeProvider claudeProvider;

  @Test
  void withoutStaticDataItReportsNoLiveDataRatherThanFailing() throws Exception {
    mockMvc.perform(get(URL)
        .param("transportMode", "BUS")
        .param("routeGroup", "2")
        .param("line", "117")
        .param("stopAreaId", "12273")
        .param("stopAreaName", "Skogslöparvägen")
        .param("scheduled", SL_SCHEDULED)
        .param("destination", "Brommaplan")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.outcome").value("NO_LIVE_DATA"))
      .andExpect(jsonPath("$.tripId").doesNotExist());
  }

  @Test
  void aBlankLineIsRejected() throws Exception {
    mockMvc.perform(get(URL)
        .param("transportMode", "BUS")
        .param("routeGroup", "2")
        .param("line", "  ")
        .param("stopAreaId", "12273")
        .param("stopAreaName", "Skogslöparvägen")
        .param("scheduled", SL_SCHEDULED)
        .param("destination", "Brommaplan")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isBadRequest());
  }

  @Test
  void aMissingParameterIsRejected() throws Exception {
    mockMvc.perform(get(URL)
        .param("transportMode", "BUS")
        .param("routeGroup", "2")
        .param("line", "117")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isBadRequest());
  }

  @Test
  void itRequiresAuthentication() throws Exception {
    mockMvc.perform(get(URL)
        .param("transportMode", "BUS")
        .param("routeGroup", "2")
        .param("line", "117")
        .param("stopAreaId", "12273")
        .param("stopAreaName", "Skogslöparvägen")
        .param("scheduled", SL_SCHEDULED)
        .param("destination", "Brommaplan"))
      .andExpect(status().isUnauthorized());
  }
}
