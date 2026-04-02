package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deviation_history")
@Getter
@Setter
public class DeviationHistory {
  @TableGenerator(name = "id_generator_deviation_history", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "deviation_history_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_deviation_history")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "hash", nullable = false, length = 64)
  private String hash;

  @Column(name = "deviation_text", nullable = false, length = 2000)
  private String deviationText;

  @Column(name = "from_date")
  private LocalDate fromDate;

  @Column(name = "to_date")
  private LocalDate toDate;

  @Column(name = "accessibility")
  private Boolean accessibility;

  @Column(name = "delays")
  private Boolean delays;

  @Column(name = "cancelations")
  private Boolean cancelations;

  @Column(name = "during_commute")
  private Boolean duringCommute;

  @Column(name = "during_weekend")
  private Boolean duringWeekend;

  @Column(name = "during_night")
  private Boolean duringNight;

  @Enumerated(EnumType.STRING)
  @Column(name = "importance", nullable = false, length = 10)
  private Importance importance;

  @Column(name = "interpretation_notes", length = 2000)
  private String interpretationNotes;

  @Column(name = "ai_error", nullable = false)
  private boolean aiError;

  @Column(name = "original_create_date", nullable = false)
  private LocalDateTime originalCreateDate;

  @CreationTimestamp
  @Column(name = "create_date")
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;

  public static DeviationHistory from(DeviationInterpretation interpretation) {
    DeviationHistory history = new DeviationHistory();
    history.setHash(interpretation.getHash());
    history.setDeviationText(interpretation.getDeviationText());
    history.setFromDate(interpretation.getFromDate());
    history.setToDate(interpretation.getToDate());
    history.setAccessibility(interpretation.getAccessibility());
    history.setDelays(interpretation.getDelays());
    history.setCancelations(interpretation.getCancelations());
    history.setDuringCommute(interpretation.getDuringCommute());
    history.setDuringWeekend(interpretation.getDuringWeekend());
    history.setDuringNight(interpretation.getDuringNight());
    history.setImportance(interpretation.getImportance());
    history.setInterpretationNotes(interpretation.getInterpretationNotes());
    history.setAiError(interpretation.isAiError());
    history.setOriginalCreateDate(interpretation.getCreateDate());
    return history;
  }
}
