package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AccessRequestResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.AllowedUserResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.AccessRequestMapper;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.AllowedUserMapper;
import com.tarnvik.publicbackend.commuter.service.AccessRequestService;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AccessRequestService accessRequestService;
  private final AllowedUserService allowedUserService;
  private final AccessRequestMapper accessRequestMapper;
  private final AllowedUserMapper allowedUserMapper;

  @GetMapping("/access-requests/count")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Long> countAccessRequests() {
    return ResponseEntity.ok(accessRequestService.countAccessRequests());
  }

  @GetMapping("/access-requests")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<AccessRequestResponse>> listAccessRequests() {
    List<AccessRequestResponse> responses = accessRequestService.listAccessRequests().stream()
      .map(accessRequestMapper::toResponse)
      .toList();
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/access-requests/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> approveAccessRequest(@PathVariable Long id) {
    accessRequestService.approveAccessRequest(id);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/access-requests/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> rejectAccessRequest(@PathVariable Long id) {
    accessRequestService.rejectAccessRequest(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<AllowedUserResponse>> listUsers() {
    List<AllowedUserResponse> responses = allowedUserService.listUsers().stream()
      .map(allowedUserMapper::toResponse)
      .toList();
    return ResponseEntity.ok(responses);
  }

  @DeleteMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    allowedUserService.deleteUser(id);
    return ResponseEntity.ok().build();
  }
}
