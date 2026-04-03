package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protected")
@RequiredArgsConstructor
public class AccountController {
  private final AllowedUserService allowedUserService;

  @DeleteMapping("/account")
  public ResponseEntity<Void> deleteAccount(AllowedUser user, HttpServletRequest request) {
    allowedUserService.deleteOwnAccount(user);
    request.getSession().invalidate();
    return ResponseEntity.ok().build();
  }
}
