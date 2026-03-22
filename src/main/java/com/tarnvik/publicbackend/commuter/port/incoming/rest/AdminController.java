package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final AdminService adminService;

  @GetMapping("/access-requests")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<AccessRequestResponse>> listAccessRequests() {
    List<AccessRequestResponse> responses = adminService.listAccessRequests().stream()
      .map(r -> new AccessRequestResponse(r.getId(), r.getEmail(), r.getName(), formatDate(r.getCreateDate())))
      .toList();
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/access-requests/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> approveAccessRequest(@PathVariable Long id) {
    adminService.approveAccessRequest(id);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/access-requests/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> rejectAccessRequest(@PathVariable Long id) {
    adminService.rejectAccessRequest(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<AllowedUserResponse>> listUsers() {
    List<AllowedUserResponse> responses = adminService.listUsers().stream()
      .map(u -> new AllowedUserResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(), formatDate(u.getCreateDate())))
      .toList();
    return ResponseEntity.ok(responses);
  }

  @DeleteMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    adminService.deleteUser(id);
    return ResponseEntity.ok().build();
  }

  private String formatDate(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.format(DATE_FORMATTER);
  }

  private record AccessRequestResponse(Long id, String email, String name, String createDate) {}

  private record AllowedUserResponse(Long id, String email, String name, String role, String createDate) {}
}
