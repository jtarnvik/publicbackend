package com.tarnvik.publicbackend.commuter.port.outgoing.terminal.items;

import com.tarnvik.publicbackend.commuter.port.outgoing.terminal.DashboardItem;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Header: which build is actually running.
 * <p>
 * The deployment is tag-driven — {@code just pull} checks out the highest version tag — so this is
 * the quickest confirmation that a deploy moved to the version that was intended. The build
 * timestamp is shown alongside because two builds of the same version are otherwise
 * indistinguishable on screen.
 */
@Component
@Profile("production")
@Order(10)
public class VersionItem extends DashboardItem {
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Stockholm"));

  private static final String HEADING = "SL-Commuter Service";

  private static final String UNKNOWN = "unknown";

  /**
   * When this process started, taken from the JVM rather than captured on bean creation so it
   * reflects the real start rather than however far into startup this bean happened to be built.
   * Constant for the lifetime of the process, so it is formatted once.
   */
  private static final String STARTED_AT = TIMESTAMP_FORMAT.format(
    Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));

  /**
   * Optional on purpose. The bean exists whenever the app runs from a jar built by Maven, but
   * resolving it lazily means a build without build-info shows "unknown" rather than refusing to
   * start the dashboard.
   */
  private final ObjectProvider<BuildProperties> buildProperties;

  public VersionItem(ObjectProvider<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Override
  public int rowCount() {
    return 5;
  }

  @Override
  public void redraw(Terminal terminal, int startRow, int maxRows, int maxCols) {
    BuildProperties build = buildProperties.getIfAvailable();

    printAt(terminal, HEADING, startRow, 1, maxRows, maxCols,
      AttributedStyle.WHITE, AttributedStyle.BLUE, true);

    printAt(terminal, "Version:", startRow + 1, 1, maxRows, maxCols);
    printAt(terminal, build == null ? UNKNOWN : build.getVersion(),
      startRow + 1, VALUE_COLUMN, maxRows, maxCols);

    printAt(terminal, "Built:", startRow + 2, 1, maxRows, maxCols);
    printAt(terminal, build == null || build.getTime() == null ? UNKNOWN : TIMESTAMP_FORMAT.format(build.getTime()),
      startRow + 2, VALUE_COLUMN, maxRows, maxCols);

    printAt(terminal, "Started:", startRow + 3, 1, maxRows, maxCols);
    printAt(terminal, STARTED_AT, startRow + 3, VALUE_COLUMN, maxRows, maxCols);
  }
}
