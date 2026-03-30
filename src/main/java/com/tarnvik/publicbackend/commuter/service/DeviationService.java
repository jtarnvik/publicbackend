package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviationService {

  public List<DeviationInterpretationResult> interpretDeviations(List<String> deviationTexts, String email) {
    // Stub — logic implemented in A2
    return List.of();
  }

  public void hideDeviation(Long deviationId, String email) {
    // Stub — logic implemented in A2
  }
}
