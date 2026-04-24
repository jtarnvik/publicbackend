package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import com.tarnvik.publicbackend.commuter.service.GtfsAccessService;
import com.tarnvik.publicbackend.config.AllowedUserArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GtfsController.class,
            excludeAutoConfiguration = {
              SecurityAutoConfiguration.class,
              SecurityFilterAutoConfiguration.class,
              ServletWebSecurityAutoConfiguration.class,
              OAuth2ClientAutoConfiguration.class,
              OAuth2ClientWebSecurityAutoConfiguration.class
            })
class GtfsControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean AllowedUserArgumentResolver allowedUserArgumentResolver;
  @MockitoBean GtfsAccessService gtfsAccessService;

  @Test
  void getRouteGroups_returnsGroupsWithCorrectFieldNamesAndValues() throws Exception {
    when(gtfsAccessService.getMonitoredRouteGroups()).thenReturn(List.of(
      new MonitoredRouteGroupResponse("TRAIN", 1, "43/44"),
      new MonitoredRouteGroupResponse("METRO", 1, "17/18/19")
    ));

    mockMvc.perform(get("/api/protected/gtfs/route-groups"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].transportMode").value("TRAIN"))
      .andExpect(jsonPath("$[0].routeGroup").value(1))
      .andExpect(jsonPath("$[0].displayName").value("43/44"))
      .andExpect(jsonPath("$[1].transportMode").value("METRO"))
      .andExpect(jsonPath("$[1].routeGroup").value(1))
      .andExpect(jsonPath("$[1].displayName").value("17/18/19"));
  }
}
