package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeleteAccountTest {

  private static final String USER_EMAIL = "deleteaccount-user@example.com";
  private static final String ADMIN1_EMAIL = "deleteaccount-admin1@example.com";
  private static final String ADMIN2_EMAIL = "deleteaccount-admin2@example.com";
  private static final String TEST_NAME = "Delete Account Test User";

  @Autowired MockMvc mockMvc;
  @Autowired AllowedUserService allowedUserService;
  @Autowired AllowedUserRepository allowedUserRepository;

  @MockitoBean ClaudeProvider claudeProvider;
  @MockitoBean PushoverProvider pushoverProvider;

  // Saves roles of any pre-existing users that we demote temporarily
  private final Map<String, String> savedRoles = new HashMap<>();

  @BeforeEach
  void setUp() {
    // Demote all pre-existing admins so tests start with zero admins from seed data.
    // Roles are restored in @AfterEach to leave the database clean for other tests.
    allowedUserRepository.findAll().stream()
      .filter(u -> u.getRole() != null)
      .forEach(user -> {
        savedRoles.put(user.getEmail(), user.getRole());
        user.setRole(null);
        allowedUserRepository.save(user);
      });
  }

  @AfterEach
  void cleanup() {
    allowedUserRepository.deleteByEmail(USER_EMAIL);
    allowedUserRepository.deleteByEmail(ADMIN1_EMAIL);
    allowedUserRepository.deleteByEmail(ADMIN2_EMAIL);
    // Restore original roles of pre-existing users
    savedRoles.forEach((email, role) ->
      allowedUserRepository.findByEmail(email).ifPresent(user -> {
        user.setRole(role);
        allowedUserRepository.save(user);
      })
    );
    savedRoles.clear();
  }

  @Test
  void regularUser_canDeleteOwnAccount() throws Exception {
    allowedUserService.createUser(USER_EMAIL, TEST_NAME);

    mockMvc.perform(delete("/api/protected/account")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", USER_EMAIL))))
      .andExpect(status().isOk());

    assertThat(allowedUserRepository.findByEmail(USER_EMAIL)).isEmpty();
  }

  @Test
  void lastAdmin_cannotDeleteOwnAccount() throws Exception {
    allowedUserService.createUser(ADMIN1_EMAIL, TEST_NAME);
    setRole(ADMIN1_EMAIL, "ADMIN");

    mockMvc.perform(delete("/api/protected/account")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", ADMIN1_EMAIL))))
      .andExpect(status().isConflict());

    assertThat(allowedUserRepository.findByEmail(ADMIN1_EMAIL)).isPresent();
  }

  @Test
  void adminWithAnotherAdminPresent_canDeleteOwnAccount() throws Exception {
    allowedUserService.createUser(ADMIN1_EMAIL, TEST_NAME);
    allowedUserService.createUser(ADMIN2_EMAIL, TEST_NAME);
    setRole(ADMIN1_EMAIL, "ADMIN");
    setRole(ADMIN2_EMAIL, "ADMIN");

    mockMvc.perform(delete("/api/protected/account")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", ADMIN1_EMAIL))))
      .andExpect(status().isOk());

    assertThat(allowedUserRepository.findByEmail(ADMIN1_EMAIL)).isEmpty();
    assertThat(allowedUserRepository.findByEmail(ADMIN2_EMAIL)).isPresent();
  }

  private void setRole(String email, String role) {
    AllowedUser user = allowedUserRepository.findByEmail(email).orElseThrow();
    user.setRole(role);
    allowedUserRepository.save(user);
  }
}
