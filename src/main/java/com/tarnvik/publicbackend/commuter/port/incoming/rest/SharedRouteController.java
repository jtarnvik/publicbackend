package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.CreateSharedRouteRequest;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.CreateSharedRouteResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SharedRouteDataResponse;
import com.tarnvik.publicbackend.commuter.service.SharedRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SharedRouteController {
  private final SharedRouteService sharedRouteService;

  @PostMapping("/api/protected/routes")
  public ResponseEntity<CreateSharedRouteResponse> createSharedRoute(
    AllowedUser user,
    @Valid @RequestBody CreateSharedRouteRequest request
  ) {
    String id = sharedRouteService.create(request.routeData());
    return ResponseEntity.ok(new CreateSharedRouteResponse(id));
  }

  @GetMapping("/api/public/routes/{id}")
  public ResponseEntity<SharedRouteDataResponse> getSharedRoute(@PathVariable String id) {
    return sharedRouteService.findById(id)
      .map(route -> ResponseEntity.ok(new SharedRouteDataResponse(route.getRouteData())))
      .orElse(ResponseEntity.notFound().build());
  }
}
