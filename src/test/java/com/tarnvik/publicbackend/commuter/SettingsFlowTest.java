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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
