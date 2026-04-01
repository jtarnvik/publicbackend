package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserSettingsRepository;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import com.tarnvik.publicbackend.commuter.service.UserSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsCascadeDeleteTest {

  private static final String TEST_EMAIL = "cascade@example.com";
  private static final String TEST_NAME = "Cascade User";

  @Autowired MockMvc mockMvc;
  @Autowired AllowedUserService allowedUserService;
  @Autowired UserSettingsService userSettingsService;
  @Autowired AllowedUserRepository allowedUserRepository;
  @Autowired UserSettingsRepository userSettingsRepository;

  @MockitoBean PushoverProvider pushoverProvider;
  @MockitoBean ClaudeProvider claudeProvider;

  @AfterEach
  void cleanup() {
    allowedUserRepository.deleteByEmail(TEST_EMAIL);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void deletingUserAlsoCascadesSettings() throws Exception {
    allowedUserService.createUser(TEST_EMAIL, TEST_NAME);
    userSettingsService.saveSettings(TEST_EMAIL, "9091001000003715", "Skogslöparvägen");

    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isPresent();

    Long userId = allowedUserRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();
    mockMvc.perform(delete("/api/admin/users/" + userId))
      .andExpect(status().isOk());

    assertThat(allowedUserRepository.findByEmail(TEST_EMAIL)).isEmpty();
    assertThat(userSettingsRepository.findByAllowedUserEmail(TEST_EMAIL)).isEmpty();
  }
}
