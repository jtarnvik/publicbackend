package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record AccessRequestResponse(Long id, String email, String name, String createDate) {}
