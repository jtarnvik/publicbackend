package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.VersionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The running backend version, for the frontend's about dialog.
 * <p>
 * Public rather than protected: the version identifies a build, not a user, and keeping it out of the
 * authenticated chain means the dialog can still say something useful if the about dialog is ever shown
 * before login.
 */
@RestController
@RequiredArgsConstructor
public class VersionController {
  private static final String UNKNOWN = "unknown";

  /**
   * Optional for the same reason as in {@code VersionItem} — the bean exists only when the app runs from a
   * Maven-built jar, and a build without build-info should report "unknown" rather than fail to start.
   */
  private final ObjectProvider<BuildProperties> buildProperties;

  @GetMapping("/api/public/version")
  public ResponseEntity<VersionResponse> getVersion() {
    BuildProperties build = buildProperties.getIfAvailable();
    return ResponseEntity.ok(new VersionResponse(build == null ? UNKNOWN : build.getVersion()));
  }
}
