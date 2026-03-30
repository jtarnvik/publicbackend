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
  private Optional<LocalDate> from;
  private Optional<LocalDate> to;
  private Optional<Boolean> accessibility;
  private Optional<Boolean> delays;
  private Optional<Boolean> cancelations;
  private Optional<Boolean> duringCommute;
  private Optional<Boolean> duringWeekend;
  private Optional<Boolean> duringNight;
  private Importance importance;
  private String interpretationNotes;
}
