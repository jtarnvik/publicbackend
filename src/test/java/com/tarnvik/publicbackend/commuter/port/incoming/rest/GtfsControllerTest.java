package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDataStatusResponse;
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

import static org.hamcrest.Matchers.nullValue;
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
      MonitoredRouteGroupResponse.builder().transportMode("TRAIN").routeGroup(1).displayName("43/44").onlyFocused(false).build(),
      MonitoredRouteGroupResponse.builder().transportMode("METRO").routeGroup(1).displayName("17/18/19")
        .focusStart("9021001001009001").focusEnd("9021001001007001").onlyFocused(true).build()
    ));

    mockMvc.perform(get("/api/protected/gtfs/route-groups"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].transportMode").value("TRAIN"))
      .andExpect(jsonPath("$[0].routeGroup").value(1))
      .andExpect(jsonPath("$[0].displayName").value("43/44"))
      .andExpect(jsonPath("$[0].focusStart", nullValue()))
      .andExpect(jsonPath("$[0].focusEnd", nullValue()))
      .andExpect(jsonPath("$[0].onlyFocused").value(false))
      .andExpect(jsonPath("$[1].transportMode").value("METRO"))
      .andExpect(jsonPath("$[1].routeGroup").value(1))
      .andExpect(jsonPath("$[1].displayName").value("17/18/19"))
      .andExpect(jsonPath("$[1].focusStart").value("9021001001009001"))
      .andExpect(jsonPath("$[1].focusEnd").value("9021001001007001"))
      .andExpect(jsonPath("$[1].onlyFocused").value(true));
  }

  @Test
  void getDataStatus_whenDatasetLoaded_returnsStaticDataAvailableTrue() throws Exception {
    when(gtfsAccessService.getDataStatus()).thenReturn(new GtfsDataStatusResponse("2026-04-25", "PARSE_DONE", true));

    mockMvc.perform(get("/api/protected/gtfs/status"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.staticDataAvailable").value(true))
      .andExpect(jsonPath("$.date").value("2026-04-25"))
      .andExpect(jsonPath("$.status").value("PARSE_DONE"));
  }

  @Test
  void getDataStatus_whenDatasetEmpty_returnsStaticDataAvailableFalse() throws Exception {
    when(gtfsAccessService.getDataStatus()).thenReturn(new GtfsDataStatusResponse("2026-04-25", "ERROR_IN_PARSE", false));

    mockMvc.perform(get("/api/protected/gtfs/status"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.staticDataAvailable").value(false))
      .andExpect(jsonPath("$.date").value("2026-04-25"))
      .andExpect(jsonPath("$.status").value("ERROR_IN_PARSE"));
  }
}
