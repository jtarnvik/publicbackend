package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the comma-separated {@code app.allowed-origins} allowlist.
 * <p>
 * Worth a test because the failure mode is opaque: Spring's {@code CorsFilter} runs ahead of the security
 * chain, so a rejected origin yields a bare {@code 403 Invalid CORS request} from every endpoint at once —
 * which reads like a broken backend rather than a misconfigured one. This is exactly what a dev frontend
 * pointed at the deployed backend hit during the Render-to-Mac-Mini migration.
 * <p>
 * The whitespace after the comma is deliberate: hand-edited env files get spaces, and an untrimmed origin
 * never matches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.allowed-origins=https://sl.tarnvik.com, http://localhost:5173")
class CorsAllowedOriginsTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean PushoverProvider pushoverProvider;
  @MockitoBean ClaudeProvider claudeProvider;

  @Test
  void firstAllowedOrigin_isAccepted() throws Exception {
    mockMvc.perform(get("/ping").header("Origin", "https://sl.tarnvik.com"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "https://sl.tarnvik.com"));
  }

  @Test
  void secondAllowedOrigin_isAccepted() throws Exception {
    mockMvc.perform(get("/ping").header("Origin", "http://localhost:5173"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void unlistedOrigin_isRejected() throws Exception {
    mockMvc.perform(get("/ping").header("Origin", "https://evil.example.com"))
      .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedApiCallFromAllowedOrigin_returns401NotCorsRejection() throws Exception {
    mockMvc.perform(get("/api/auth/me").header("Origin", "http://localhost:5173"))
      .andExpect(status().isUnauthorized());
  }
}
