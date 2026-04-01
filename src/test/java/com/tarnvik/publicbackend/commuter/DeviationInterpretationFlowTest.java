package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretationError;
import com.tarnvik.publicbackend.commuter.model.domain.entity.Importance;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationErrorRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserHiddenDeviationRepository;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviationInterpretationFlowTest {

  private static final String TEST_EMAIL = "deviation@example.com";
  private static final String TEST_NAME = "Deviation Test User";
  private static final String DEVIATION_TEXT = "Pendeltåg linje 43 - Inställda avgångar på grund av tekniskt fel";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired AllowedUserService allowedUserService;
  @Autowired AllowedUserRepository allowedUserRepository;
  @Autowired DeviationInterpretationRepository deviationInterpretationRepository;
  @Autowired DeviationInterpretationErrorRepository deviationInterpretationErrorRepository;
  @Autowired UserHiddenDeviationRepository userHiddenDeviationRepository;

  @MockitoBean ClaudeProvider claudeProvider;
  @MockitoBean PushoverProvider pushoverProvider;

  @BeforeEach
  void setup() {
    allowedUserService.createUser(TEST_EMAIL, TEST_NAME);
  }

  @AfterEach
  void cleanup() {
    userHiddenDeviationRepository.deleteAll();
    deviationInterpretationRepository.deleteAll();
    deviationInterpretationErrorRepository.deleteAll();
    allowedUserRepository.deleteByEmail(TEST_EMAIL);
  }

  @Test
  void newDeviation_callsClaudeAndReturnsShown() throws Exception {
    when(claudeProvider.interpretDeviation(DEVIATION_TEXT)).thenReturn(successResponse(Importance.HIGH));

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(interpretBody(DEVIATION_TEXT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("SHOWN"))
      .andExpect(jsonPath("$[0].importance").value("HIGH"));

    verify(claudeProvider, times(1)).interpretDeviation(DEVIATION_TEXT);
  }

  @Test
  void cachedDeviation_doesNotCallClaudeAgain() throws Exception {
    when(claudeProvider.interpretDeviation(DEVIATION_TEXT)).thenReturn(successResponse(Importance.MEDIUM));

    String body = interpretBody(DEVIATION_TEXT);
    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("SHOWN"));

    verify(claudeProvider, times(1)).interpretDeviation(DEVIATION_TEXT);
  }

  @Test
  void accessibilityDeviation_isHiddenAsAccessibility() throws Exception {
    DeviationResponse response = successResponse(Importance.LOW);
    response.setAccessibility(true);
    when(claudeProvider.interpretDeviation(DEVIATION_TEXT)).thenReturn(response);

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(interpretBody(DEVIATION_TEXT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("HIDDEN_ACCESSIBILITY"));
  }

  @Test
  void aiFailure_returnsUnknown() throws Exception {
    when(claudeProvider.interpretDeviation(DEVIATION_TEXT))
      .thenThrow(new RuntimeException("AI unavailable"));

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(interpretBody(DEVIATION_TEXT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("UNKNOWN"));
  }

  @Test
  void lockedDeviation_doesNotCallClaude() throws Exception {
    DeviationInterpretationError errorTracker = new DeviationInterpretationError(sha256(DEVIATION_TEXT));
    errorTracker.setErrorCount(5);
    errorTracker.setLastAttemptAt(LocalDateTime.now());
    errorTracker.setLockedUntil(LocalDateTime.now().plusHours(24));
    deviationInterpretationErrorRepository.save(errorTracker);

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(interpretBody(DEVIATION_TEXT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("UNKNOWN"));

    verify(claudeProvider, never()).interpretDeviation(any());
  }

  @Test
  void successAfterPartialErrors_clearsErrorTracker() throws Exception {
    String hash = sha256(DEVIATION_TEXT);
    DeviationInterpretationError errorTracker = new DeviationInterpretationError(hash);
    errorTracker.setErrorCount(3);
    errorTracker.setLastAttemptAt(LocalDateTime.now());
    deviationInterpretationErrorRepository.save(errorTracker);

    when(claudeProvider.interpretDeviation(DEVIATION_TEXT)).thenReturn(successResponse(Importance.MEDIUM));

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(interpretBody(DEVIATION_TEXT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("SHOWN"));

    assertThat(deviationInterpretationErrorRepository.findByHash(hash)).isEmpty();
  }

  @Test
  void hideDeviation_subsequentCallReturnsHiddenByUser() throws Exception {
    when(claudeProvider.interpretDeviation(DEVIATION_TEXT)).thenReturn(successResponse(Importance.MEDIUM));

    String body = interpretBody(DEVIATION_TEXT);
    String resultJson = mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    List<DeviationInterpretationResult> results = objectMapper.readValue(resultJson, new TypeReference<>() {});
    long id = results.getFirst().id();

    mockMvc.perform(post("/api/protected/deviations/" + id + "/hide")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL)))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("HIDDEN_BY_USER"));
  }

  @Test
  void hideNonExistentDeviation_returns404() throws Exception {
    mockMvc.perform(post("/api/protected/deviations/99999/hide")
        .with(oauth2Login().attributes(attrs -> attrs.put("email", TEST_EMAIL))))
      .andExpect(status().isNotFound());
  }

  private DeviationResponse successResponse(Importance importance) {
    DeviationResponse response = new DeviationResponse();
    response.setImportance(importance);
    response.setAccessibility(false);
    return response;
  }

  private String interpretBody(String text) {
    return "{\"deviationTexts\":[\"" + text + "\"]}";
  }

  private static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
