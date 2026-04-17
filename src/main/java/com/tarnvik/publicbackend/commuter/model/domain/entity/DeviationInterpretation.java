package com.tarnvik.publicbackend.commuter.model.domain.entity;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
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
@Table(name = "deviation_interpretations")
@Getter
@Setter
public class DeviationInterpretation {
  @TableGenerator(name = "id_generator_deviation_interpretations", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "deviation_interpretations_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_deviation_interpretations")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "hash", nullable = false, unique = true, length = 64)
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

  @CreationTimestamp
  @Column(name = "create_date", updatable = false)
  private LocalDateTime createDate;

  public static DeviationInterpretation from(String text, String hash, DeviationResponse response) {
    DeviationInterpretation entity = new DeviationInterpretation();
    entity.setHash(hash);
    entity.setDeviationText(text);
    entity.setFromDate(response.getFromDate());
    entity.setToDate(response.getToDate());
    entity.setAccessibility(response.getAccessibility());
    entity.setDelays(response.getDelays());
    entity.setCancelations(response.getCancelations());
    entity.setDuringCommute(response.getDuringCommute());
    entity.setDuringWeekend(response.getDuringWeekend());
    entity.setDuringNight(response.getDuringNight());
    entity.setImportance(response.getImportance());
    entity.setInterpretationNotes(response.getInterpretationNotes());
    entity.setAiError(false);
    return entity;
  }

  public void updateFrom(DeviationResponse response) {
    this.fromDate = response.getFromDate();
    this.toDate = response.getToDate();
    this.accessibility = response.getAccessibility();
    this.delays = response.getDelays();
    this.cancelations = response.getCancelations();
    this.duringCommute = response.getDuringCommute();
    this.duringWeekend = response.getDuringWeekend();
    this.duringNight = response.getDuringNight();
    this.importance = response.getImportance();
    this.interpretationNotes = response.getInterpretationNotes();
    this.aiError = false;
  }

  public static DeviationInterpretation withAiError(String text, String hash) {
    DeviationInterpretation entity = new DeviationInterpretation();
    entity.setHash(hash);
    entity.setDeviationText(text);
    entity.setImportance(Importance.UNKNOWN);
    entity.setAiError(true);
    return entity;
  }

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;
}
