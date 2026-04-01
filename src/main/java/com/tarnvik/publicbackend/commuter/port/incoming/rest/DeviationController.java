package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.InterpretDeviationsRequest;
import com.tarnvik.publicbackend.commuter.service.DeviationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protected/deviations")
@RequiredArgsConstructor
public class DeviationController {

  private final DeviationService deviationService;

  @PostMapping("/interpret")
  public ResponseEntity<List<DeviationInterpretationResult>> interpretDeviations(
    AllowedUser user,
    @Valid @RequestBody InterpretDeviationsRequest request
  ) {
    List<DeviationInterpretationResult> results = deviationService.interpretDeviations(request.deviationTexts(), user);
    return ResponseEntity.ok(results);
  }

  @PostMapping("/{id}/hide")
  public ResponseEntity<Void> hideDeviation(
    AllowedUser user,
    @PathVariable Long id
  ) {
    deviationService.hideDeviation(id, user);
    return ResponseEntity.ok().build();
  }
}
