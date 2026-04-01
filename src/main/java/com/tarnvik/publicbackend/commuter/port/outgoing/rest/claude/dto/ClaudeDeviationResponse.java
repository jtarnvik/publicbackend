package com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.dto;

import com.tarnvik.publicbackend.commuter.model.domain.entity.Importance;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ClaudeDeviationResponse {
  private Optional<LocalDate> from = Optional.empty();
  private Optional<LocalDate> to = Optional.empty();
  private Optional<Boolean> accessibility = Optional.empty();
  private Optional<Boolean> delays = Optional.empty();
  private Optional<Boolean> cancelations = Optional.empty();
  private Optional<Boolean> duringCommute = Optional.empty();
  private Optional<Boolean> duringWeekend = Optional.empty();
  private Optional<Boolean> duringNight = Optional.empty();
  private Importance importance;
  private String interpretationNotes;
}
