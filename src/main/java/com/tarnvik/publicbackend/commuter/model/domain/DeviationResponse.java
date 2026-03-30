package com.tarnvik.publicbackend.commuter.model.domain;

import com.tarnvik.publicbackend.commuter.model.domain.entity.Importance;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DeviationResponse {
  private LocalDate fromDate;
  private LocalDate toDate;
  private Boolean accessibility;
  private Boolean delays;
  private Boolean cancelations;
  private Boolean duringCommute;
  private Boolean duringWeekend;
  private Boolean duringNight;
  private Importance importance;
  private String interpretationNotes;
}
