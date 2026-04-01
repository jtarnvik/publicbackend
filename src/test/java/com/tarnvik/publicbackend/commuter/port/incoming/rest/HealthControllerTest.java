package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.config.AllowedUserArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class,
            excludeAutoConfiguration = {
              SecurityAutoConfiguration.class,
              SecurityFilterAutoConfiguration.class,
              ServletWebSecurityAutoConfiguration.class,
              OAuth2ClientAutoConfiguration.class,
              OAuth2ClientWebSecurityAutoConfiguration.class
            })
class HealthControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean AllowedUserArgumentResolver allowedUserArgumentResolver;

  @Test
  void ping_returnsOk() throws Exception {
    mockMvc.perform(get("/ping"))
      .andExpect(status().isOk())
      .andExpect(content().string("ok"));
  }
}
