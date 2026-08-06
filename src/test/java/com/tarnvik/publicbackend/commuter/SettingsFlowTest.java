package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserSettingsRepository;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsFlowTest {

  private static final String TEST_EMAIL = "settings-flow@example.com";
  private static final String TEST_NAME = "Settings Flow Test User";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired AllowedUserService allowedUserService;
  @Autowired AllowedUserRepository allowedUserRepository;
  @Autowired UserSettingsRepository userSettingsRepository;

  @MockitoBean ClaudeProvider claudeProvider;
  @MockitoBean PushoverProvider pushoverProvider;

  @BeforeEach
  void setup() {
    allowedUserService.createUser(TEST_EMAIL, TEST_NAME);
  }

  @AfterEach
  void cleanup() {
    allowedUserRepository.deleteByEmail(TEST_EMAIL);
  }

  // --- PUT /api/protected/settings ---

  @Test
  void saveSettings_withoutAuth_returns401() throws Exception {
    String body = objectMapper.writeValueAsString(
      Map.of("stopPointId", "9091001000003715", "stopPointName", "Skogslöparvägen", "useAiInterpretation", true));

    mockMvc.perform(put("/api/protected/settings")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void saveSettings_withAuth_persistsSettings() throws Exception {
    String body = objectMapper.writeValueAsString(
      Map.of("stopPointId", "9091001000003715", "stopPointName", "Skogslöparvägen", "useAiInterpretation", true));

    mockMvc.perform(put("/api/protected/settings")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getStopPointId()).isEqualTo("9091001000003715");
    assertThat(settings.getStopPointName()).isEqualTo("Skogslöparvägen");
    assertThat(settings.isUseAiInterpretation()).isTrue();
  }

  // --- Favourite stops, saved as part of PUT /api/protected/settings ---

  /** Body with an explicit favouriteStops value. Map.of cannot hold nulls, hence the raw JSON. */
  private String settingsBody(String favouriteStopsJson) {
    return """
      {"stopPointId":"9091001000003715","stopPointName":"Skogslöparvägen","useAiInterpretation":true%s}
      """.formatted(favouriteStopsJson == null ? "" : ",\"favouriteStops\":" + favouriteStopsJson);
  }

  private void putSettings(String body) throws Exception {
    mockMvc.perform(put("/api/protected/settings")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());
  }

  private static String favourite(String id, String name) {
    return "{\"stopId\":\"%s\",\"stopName\":\"%s\"}".formatted(id, name);
  }

  @Test
  void saveSettings_withFavouriteStops_persistsThem() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov")
      + "," + favourite("9021001006081000", "Kungsängen") + "]"));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).hasSize(2);
    assertThat(settings.getFavouriteStops().getFirst().stopId()).isEqualTo("9021001001241000");
    assertThat(settings.getFavouriteStops().getFirst().stopName()).isEqualTo("Åkeshov");
  }

  /**
   * The important one: a user on a cached older frontend bundle sends no favouriteStops at all. That must
   * leave the stored list alone rather than clearing it or failing the whole save.
   */
  @Test
  void saveSettings_withoutFavouriteStopsField_leavesExistingUnchanged() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov") + "]"));

    putSettings(settingsBody(null));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).hasSize(1);
  }

  @Test
  void saveSettings_withEmptyFavouriteStops_clearsThem() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov") + "]"));

    putSettings(settingsBody("[]"));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).isEmpty();
  }

  @Test
  void saveSettings_withMoreThanTenFavourites_truncatesSilently() throws Exception {
    StringBuilder favourites = new StringBuilder("[");
    for (int i = 0; i < 13; i++) {
      favourites.append(i > 0 ? "," : "").append(favourite("902100100000000" + i, "Stop " + i));
    }
    favourites.append("]");

    putSettings(settingsBody(favourites.toString()));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).hasSize(10);
  }

  /** A stop can appear in two route groups, so ticking it twice must not consume two of the ten. */
  @Test
  void saveSettings_withDuplicateStopIds_dedupes() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001012025000", "Alvik")
      + "," + favourite("9021001012025000", "Alvik") + "]"));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).hasSize(1);
  }

  @Test
  void saveSettings_withBlankStopId_dropsIt() throws Exception {
    putSettings(settingsBody("[{\"stopId\":\"\",\"stopName\":\"Trasig\"},"
      + favourite("9021001001241000", "Åkeshov") + "]"));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getFavouriteStops()).hasSize(1);
    assertThat(settings.getFavouriteStops().getFirst().stopId()).isEqualTo("9021001001241000");
  }

  /** saveSettings writes two lists now; recentStops must still be none of its business. */
  @Test
  void saveSettings_doesNotClobberRecentStops() throws Exception {
    mockMvc.perform(post("/api/protected/settings/recent-stops")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(
          Map.of("stopPointId", "1001", "stopPointName", "Första hållplatsen"))))
      .andExpect(status().isOk());

    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov") + "]"));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getRecentStops()).hasSize(1);
    assertThat(settings.getFavouriteStops()).hasSize(1);
  }

  /**
   * Also pins the JSON field names of SettingsResponse, which changed from a record to @Value @Builder —
   * useAiInterpretation is the one a bad Lombok getter name would silently rename.
   */
  @Test
  void me_returnsFavouriteStopsAndKeepsSettingsFieldNames() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov") + "]"));

    mockMvc.perform(get("/api/auth/me")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.settings.useAiInterpretation").value(true))
      .andExpect(jsonPath("$.settings.favouriteStops.length()").value(1))
      .andExpect(jsonPath("$.settings.favouriteStops[0].stopId").value("9021001001241000"))
      .andExpect(jsonPath("$.settings.favouriteStops[0].stopName").value("Åkeshov"));
  }

  // --- PUT /api/protected/settings/live-traffic-view ---

  private void putLiveTrafficView(String body) throws Exception {
    mockMvc.perform(put("/api/protected/settings/live-traffic-view")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());
  }

  /** Map.of cannot hold a null focused, hence the raw JSON. */
  private static String liveTrafficViewBody(String transportMode, int routeGroup, Boolean focused) {
    return "{\"transportMode\":\"%s\",\"routeGroup\":%d,\"focused\":%s}"
      .formatted(transportMode, routeGroup, focused == null ? "null" : focused.toString());
  }

  @Test
  void saveLiveTrafficView_withoutAuth_returns401() throws Exception {
    mockMvc.perform(put("/api/protected/settings/live-traffic-view")
        .contentType(MediaType.APPLICATION_JSON)
        .content(liveTrafficViewBody("TRAIN", 1, true)))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void saveLiveTrafficView_withNoExistingSettingsRow_createsRowWithView() throws Exception {
    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isEmpty();

    putLiveTrafficView(liveTrafficViewBody("TRAIN", 1, false));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getLiveTrafficTransportMode()).isEqualTo("TRAIN");
    assertThat(settings.getLiveTrafficRouteGroup()).isEqualTo(1);
    assertThat(settings.getLiveTrafficFocused()).isFalse();
  }

  /**
   * The one that matters: selecting a group whose focus switch is locked sends focused null, and that must
   * leave the remembered flag alone. Otherwise picking the metro would turn the train's focus back on.
   */
  @Test
  void saveLiveTrafficView_withNullFocused_leavesStoredFlagUnchanged() throws Exception {
    putLiveTrafficView(liveTrafficViewBody("TRAIN", 1, false));

    putLiveTrafficView(liveTrafficViewBody("METRO", 1, null));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getLiveTrafficTransportMode()).isEqualTo("METRO");
    assertThat(settings.getLiveTrafficFocused()).isFalse();
  }

  /** The live traffic view writes on every change, so it must stay out of the settings dialog's columns. */
  @Test
  void saveLiveTrafficView_doesNotClobberOtherSettings() throws Exception {
    putSettings(settingsBody("[" + favourite("9021001001241000", "Åkeshov") + "]"));
    postRecentStop("1001", "Första hållplatsen");

    putLiveTrafficView(liveTrafficViewBody("BUS", 2, null));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getStopPointId()).isEqualTo("9091001000003715");
    assertThat(settings.getFavouriteStops()).hasSize(1);
    assertThat(settings.getRecentStops()).hasSize(1);
    assertThat(settings.getLiveTrafficTransportMode()).isEqualTo("BUS");
  }

  /** Conversely, the settings dialog must not wipe the remembered view. */
  @Test
  void saveSettings_doesNotClobberLiveTrafficView() throws Exception {
    putLiveTrafficView(liveTrafficViewBody("TRAIN", 1, false));

    putSettings(settingsBody(null));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getLiveTrafficTransportMode()).isEqualTo("TRAIN");
    assertThat(settings.getLiveTrafficFocused()).isFalse();
  }

  @Test
  void me_returnsLiveTrafficView() throws Exception {
    putLiveTrafficView(liveTrafficViewBody("TRAIN", 1, false));

    mockMvc.perform(get("/api/auth/me")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.settings.liveTrafficView.transportMode").value("TRAIN"))
      .andExpect(jsonPath("$.settings.liveTrafficView.routeGroup").value(1))
      .andExpect(jsonPath("$.settings.liveTrafficView.focused").value(false));
  }

  /** Nothing saved yet means no view to restore — the frontend then picks its own default. */
  @Test
  void me_withNoSavedView_returnsNullLiveTrafficView() throws Exception {
    putSettings(settingsBody(null));

    mockMvc.perform(get("/api/auth/me")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.settings.liveTrafficView.transportMode").doesNotExist());
  }

  // --- POST /api/protected/settings/recent-stops ---

  @Test
  void addRecentStop_withoutAuth_returns401() throws Exception {
    String body = objectMapper.writeValueAsString(
      Map.of("stopPointId", "1234", "stopPointName", "Teststationen"));

    mockMvc.perform(post("/api/protected/settings/recent-stops")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void addRecentStop_withNoExistingSettingsRow_createsRowWithStop() throws Exception {
    // User has no settings row yet — this is the new-user case
    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isEmpty();

    String body = objectMapper.writeValueAsString(
      Map.of("stopPointId", "1001", "stopPointName", "Första hållplatsen"));

    mockMvc.perform(post("/api/protected/settings/recent-stops")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getRecentStops()).hasSize(1);
    assertThat(settings.getRecentStops().get(0).stopPointId()).isEqualTo("1001");
  }

  @Test
  void addRecentStop_duplicateStop_movesItToTop() throws Exception {
    postRecentStop("1001", "Hållplats Ett");
    postRecentStop("1002", "Hållplats Två");
    postRecentStop("1001", "Hållplats Ett"); // re-add first stop

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getRecentStops()).hasSize(2);
    assertThat(settings.getRecentStops().get(0).stopPointId()).isEqualTo("1001");
    assertThat(settings.getRecentStops().get(1).stopPointId()).isEqualTo("1002");
  }

  @Test
  void addRecentStop_exceedsMaxFive_dropsOldest() throws Exception {
    for (int i = 1; i <= 6; i++) {
      postRecentStop("100" + i, "Hållplats " + i);
    }

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getRecentStops()).hasSize(5);
    // Newest (1006) is first, oldest (1001) is dropped
    assertThat(settings.getRecentStops().get(0).stopPointId()).isEqualTo("1006");
    assertThat(settings.getRecentStops()).noneMatch(s -> s.stopPointId().equals("1001"));
  }

  // --- DELETE /api/protected/settings/recent-stops ---

  @Test
  void clearRecentStops_withoutAuth_returns401() throws Exception {
    mockMvc.perform(delete("/api/protected/settings/recent-stops"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void clearRecentStops_withExistingStops_clearsAll() throws Exception {
    postRecentStop("1001", "Hållplats Ett");
    postRecentStop("1002", "Hållplats Två");

    mockMvc.perform(delete("/api/protected/settings/recent-stops")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk());

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL).orElseThrow();
    assertThat(settings.getRecentStops()).isEmpty();
  }

  @Test
  void clearRecentStops_withNoExistingSettingsRow_isNoOp() throws Exception {
    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isEmpty();

    mockMvc.perform(delete("/api/protected/settings/recent-stops")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk());

    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isEmpty();
  }

  private void postRecentStop(String stopPointId, String stopPointName) throws Exception {
    String body = objectMapper.writeValueAsString(
      Map.of("stopPointId", stopPointId, "stopPointName", stopPointName));

    mockMvc.perform(post("/api/protected/settings/recent-stops")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());
  }
}
