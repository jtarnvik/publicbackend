package com.tarnvik.publicbackend.commuter.model.domain.entity;

import com.tarnvik.publicbackend.commuter.model.domain.FavouriteStop;
import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;
import com.tarnvik.publicbackend.commuter.model.domain.converter.FavouriteStopListConverter;
import com.tarnvik.publicbackend.commuter.model.domain.converter.RecentStopListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {
  @TableGenerator(name = "id_generator_user_settings", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "user_settings_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_user_settings")
  @Column(name = "id")
  @Id
  private Long id;

  @OneToOne
  @JoinColumn(name = "allowed_user_id", nullable = false, unique = true)
  private AllowedUser allowedUser;

  @Column(name = "stop_point_id")
  private String stopPointId;

  @Column(name = "stop_point_name")
  private String stopPointName;

  @Column(name = "use_ai_interpretation", nullable = false)
  private boolean useAiInterpretation;

  @Convert(converter = RecentStopListConverter.class)
  @Column(name = "recent_stops", columnDefinition = "TEXT")
  private List<RecentStop> recentStops = new ArrayList<>();

  /**
   * Stops to emphasise on the live traffic schematic. The converter returns an immutable empty list for
   * null or blank column data, so always copy into a new list before mutating.
   */
  @Convert(converter = FavouriteStopListConverter.class)
  @Column(name = "favourite_stops", columnDefinition = "TEXT")
  private List<FavouriteStop> favouriteStops = new ArrayList<>();

  /**
   * The route group the live traffic view showed last, as a {@link TransportMode} name plus its group
   * number. Kept as a String rather than the enum: the value is only ever matched against the route groups
   * currently served, so one that has left {@code gtfs_monitored_route} needs to miss quietly, not fail to
   * deserialize.
   */
  @Column(name = "live_traffic_transport_mode")
  private String liveTrafficTransportMode;

  @Column(name = "live_traffic_route_group")
  private Integer liveTrafficRouteGroup;

  /**
   * The focus switch position, or null if the user has never operated it. Null is not the same as false —
   * it means "fall back to the group's default", which is focused-on for every group that has a window.
   * Only written for groups where the switch is actually operable; see {@code UserSettingsService}.
   */
  @Column(name = "live_traffic_focused")
  private Boolean liveTrafficFocused;

  @CreationTimestamp
  @Column(name = "create_date", updatable = false)
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;
}
