package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.SharedRoute;
import com.tarnvik.publicbackend.commuter.model.domain.repository.SharedRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SharedRouteService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SharedRouteRepository sharedRouteRepository;

  @Transactional
  public String create(String routeData) {
    SharedRoute route = new SharedRoute();
    route.setId(generateId());
    route.setRouteData(routeData);
    sharedRouteRepository.save(route);
    return route.getId();
  }

  @Transactional(readOnly = true)
  public Optional<SharedRoute> findById(String id) {
    return sharedRouteRepository.findById(id);
  }

  private String generateId() {
    byte[] bytes = new byte[5];
    SECURE_RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
