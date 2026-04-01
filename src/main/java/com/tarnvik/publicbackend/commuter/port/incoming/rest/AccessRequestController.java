package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AccessRequestDto;
import com.tarnvik.publicbackend.commuter.service.AccessRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/access-request")
public class AccessRequestController {
  private final AccessRequestService accessRequestService;

  public AccessRequestController(AccessRequestService accessRequestService) {
    this.accessRequestService = accessRequestService;
  }

  @PostMapping
  public ResponseEntity<Void> requestAccess(@Valid @RequestBody AccessRequestDto dto) {
    accessRequestService.handleAccessRequest(dto.email().trim(), dto.message());
    return ResponseEntity.ok().build();
  }
}
