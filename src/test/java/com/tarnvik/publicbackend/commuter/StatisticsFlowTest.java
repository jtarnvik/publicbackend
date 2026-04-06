package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.model.domain.entity.Importance;
import com.tarnvik.publicbackend.commuter.model.domain.entity.StatName;
import com.tarnvik.publicbackend.commuter.model.domain.entity.Statistic;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.SharedRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.StatisticRepository;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatisticsFlowTest {

  private static final String TEST_EMAIL = "statistics@example.com";
  private static final String TEST_NAME = "Statistics Test User";
  private static final String ADMIN_EMAIL = "jtarnvik@gmail.com";
  private static final String ROUTE_DATA = "{\"legs\":[],\"duration\":42}";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired AllowedUserService allowedUserService;
  @Autowired AllowedUserRepository allowedUserRepository;
  @Autowired SharedRouteRepository sharedRouteRepository;
  @Autowired StatisticRepository statisticRepository;

  @MockitoBean ClaudeProvider claudeProvider;
  @MockitoBean PushoverProvider pushoverProvider;

  @BeforeEach
  void setup() {
    allowedUserService.createUser(TEST_EMAIL, TEST_NAME);
  }

  @AfterEach
  void cleanup() {
    sharedRouteRepository.deleteAll();
    allowedUserRepository.deleteByEmail(TEST_EMAIL);
  }

  @Test
  void createSharedRoute_incrementsRoutesSharedStat() throws Exception {
    long before = counterFor(StatName.ROUTES_SHARED);

    String body = objectMapper.writeValueAsString(Map.of("routeData", ROUTE_DATA));
    mockMvc.perform(post("/api/protected/routes")
        .with(oauth2Login().attributes(attrs -> {
          attrs.put("email", TEST_EMAIL);
          attrs.put("name", TEST_NAME);
        }))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());

    assertThat(counterFor(StatName.ROUTES_SHARED)).isEqualTo(before + 1);
  }

  @Test
  void interpretDeviations_onCacheMiss_incrementsAiQueryStat() throws Exception {
    DeviationResponse aiResponse = new DeviationResponse();
    aiResponse.setImportance(Importance.LOW);
    aiResponse.setDelays(false);
    aiResponse.setCancelations(false);
    when(claudeProvider.interpretDeviation(any())).thenReturn(aiResponse);

    long before = counterFor(StatName.AI_INTERPRETATION_QUERIES);

    // Use a UUID so the text is never in the DB cache and always triggers a Claude call
    String uniqueText = UUID.randomUUID().toString();
    String body = objectMapper.writeValueAsString(Map.of("deviationTexts", List.of(uniqueText)));
    mockMvc.perform(post("/api/protected/deviations/interpret")
        .with(oauth2Login().attributes(attrs -> {
          attrs.put("email", TEST_EMAIL);
          attrs.put("name", TEST_NAME);
        }))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk());

    assertThat(counterFor(StatName.AI_INTERPRETATION_QUERIES)).isEqualTo(before + 1);
  }

  @Test
  void getStatistics_asAdmin_returnsAllFields() throws Exception {
    mockMvc.perform(get("/api/admin/statistics")
        .with(oauth2Login()
          .attributes(attrs -> attrs.put("email", ADMIN_EMAIL))
          .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.routesShared").isNumber())
      .andExpect(jsonPath("$.aiInterpretationQueries").isNumber())
      .andExpect(jsonPath("$.userCount").isNumber());
  }

  @Test
  void getStatistics_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(get("/api/admin/statistics")
        .with(oauth2Login().attributes(attrs -> {
          attrs.put("email", TEST_EMAIL);
          attrs.put("name", TEST_NAME);
        })))
      .andExpect(status().isForbidden());
  }

  private long counterFor(StatName stat) {
    return statisticRepository.findByName(stat.name())
      .map(Statistic::getCounter)
      .orElse(0L);
  }
}
