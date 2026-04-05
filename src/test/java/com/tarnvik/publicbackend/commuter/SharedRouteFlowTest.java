package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.SharedRouteRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedRouteFlowTest {

  private static final String TEST_EMAIL = "sharedroute@example.com";
  private static final String TEST_NAME = "Shared Route Test User";
  private static final String ROUTE_DATA = "{\"legs\":[],\"duration\":42}";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired AllowedUserService allowedUserService;
  @Autowired AllowedUserRepository allowedUserRepository;
  @Autowired SharedRouteRepository sharedRouteRepository;

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
  void createSharedRoute_withoutAuth_returns401() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("routeData", ROUTE_DATA));

    mockMvc.perform(post("/api/protected/routes")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void createSharedRoute_withAuth_returnsIdWith10HexChars() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("routeData", ROUTE_DATA));

    String response = mockMvc.perform(post("/api/protected/routes")
        .with(oauth2Login().attributes(attrs -> {
          attrs.put("email", TEST_EMAIL);
          attrs.put("name", TEST_NAME);
        }))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").isString())
      .andReturn().getResponse().getContentAsString();

    String id = objectMapper.readTree(response).get("id").asText();
    assertThat(id).matches("[0-9a-f]{10}");
  }

  @Test
  void getSharedRoute_withoutAuth_returnsRouteData() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("routeData", ROUTE_DATA));

    // Create route as a logged-in user
    String createResponse = mockMvc.perform(post("/api/protected/routes")
        .with(oauth2Login().attributes(attrs -> {
          attrs.put("email", TEST_EMAIL);
          attrs.put("name", TEST_NAME);
        }))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    String id = objectMapper.readTree(createResponse).get("id").asText();

    // Retrieve without any authentication — public endpoint
    mockMvc.perform(get("/api/public/routes/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.routeData").value(ROUTE_DATA));
  }

  @Test
  void getSharedRoute_unknownId_returns404() throws Exception {
    mockMvc.perform(get("/api/public/routes/0000000000"))
      .andExpect(status().isNotFound());
  }
}
