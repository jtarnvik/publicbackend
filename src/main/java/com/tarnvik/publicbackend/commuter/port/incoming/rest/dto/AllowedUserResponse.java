package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AllowedUserResponse {
  Long id;
  String email;
  String name;
  String role;
  String createDate;
  String lastLogin;
}
