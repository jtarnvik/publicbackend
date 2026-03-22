package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record AllowedUserResponse(Long id, String email, String name, String role, String createDate) {}
