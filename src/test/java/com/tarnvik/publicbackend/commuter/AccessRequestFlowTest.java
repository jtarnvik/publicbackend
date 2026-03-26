package com.tarnvik.publicbackend.commuter;

import com.tarnvik.publicbackend.commuter.model.domain.repository.AccessRequestRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.PendingUserRepository;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AccessRequestResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.service.PendingUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessRequestFlowTest {

  private static final String TEST_EMAIL = "newuser@example.com";
  private static final String TEST_NAME = "New User";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired PendingUserService pendingUserService;
  @Autowired PendingUserRepository pendingUserRepository;
  @Autowired AccessRequestRepository accessRequestRepository;
  @Autowired AllowedUserRepository allowedUserRepository;

  @MockitoBean PushoverProvider pushoverProvider;

  @AfterEach
  void cleanup() {
    pendingUserRepository.deleteByEmail(TEST_EMAIL);
    accessRequestRepository.deleteByEmail(TEST_EMAIL);
    allowedUserRepository.deleteByEmail(TEST_EMAIL);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void fullRejectFlow() throws Exception {
    pendingUserService.recordLoginAttempt(TEST_EMAIL, TEST_NAME);

    mockMvc.perform(post("/api/public/access-request")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + TEST_EMAIL + "\",\"message\":\"Please let me in\"}"))
      .andExpect(status().isOk());

    String listJson = mockMvc.perform(get("/api/admin/access-requests"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)))
      .andReturn().getResponse().getContentAsString();

    List<AccessRequestResponse> requests = objectMapper.readValue(listJson, new TypeReference<>() {});
    long id = requests.get(0).id();

    mockMvc.perform(delete("/api/admin/access-requests/" + id))
      .andExpect(status().isOk());

    // Access request list is now empty
    mockMvc.perform(get("/api/admin/access-requests"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(0)));

    // User is NOT in the allowed users list
    mockMvc.perform(get("/api/admin/users"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[?(@.email == '" + TEST_EMAIL + "')]").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void fullAccessRequestFlow() throws Exception {
    // Seed the pending user (simulates a failed OAuth2 login attempt)
    pendingUserService.recordLoginAttempt(TEST_EMAIL, TEST_NAME);

    // No access requests yet
    mockMvc.perform(get("/api/admin/access-requests/count"))
      .andExpect(status().isOk())
      .andExpect(content().string("0"));

    // User submits an access request
    mockMvc.perform(post("/api/public/access-request")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + TEST_EMAIL + "\",\"message\":\"Please let me in\"}"))
      .andExpect(status().isOk());

    // Access request appears in the pending list
    mockMvc.perform(get("/api/admin/access-requests/count"))
      .andExpect(status().isOk())
      .andExpect(content().string("1"));

    String listJson = mockMvc.perform(get("/api/admin/access-requests"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)))
      .andExpect(jsonPath("$[0].email").value(TEST_EMAIL))
      .andExpect(jsonPath("$[0].name").value(TEST_NAME))
      .andReturn().getResponse().getContentAsString();

    // Approve the request
    List<AccessRequestResponse> requests = objectMapper.readValue(listJson, new TypeReference<>() {});
    long id = requests.get(0).id();

    mockMvc.perform(post("/api/admin/access-requests/" + id + "/approve"))
      .andExpect(status().isOk());

    // Access request list is now empty
    mockMvc.perform(get("/api/admin/access-requests"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(0)));

    // User appears in the allowed users list
    mockMvc.perform(get("/api/admin/users"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[?(@.email == '" + TEST_EMAIL + "')]").exists());
  }
}
