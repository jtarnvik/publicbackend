package com.tarnvik.publicbackend.commuter.port.outgoing.terminal.items;

import com.tarnvik.publicbackend.commuter.port.outgoing.terminal.DashboardItem;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * How many users have opened the app recently — see {@code AllowedUserService.countActiveUsers()}
 * for the window.
 * <p>
 * The count is cached in {@link #refresh()} rather than read during {@code redraw}, so a terminal
 * resize repaints from memory instead of issuing a query per WINCH signal.
 * <p>
 * <strong>Why an hourly job repaints this.</strong> The count changes for two reasons, and only one
 * of them is an event: someone opens the app, or a previous visit simply ages out of the window.
 * Nothing runs when a timestamp crosses the boundary, so without a periodic recount the figure
 * would sit stale until the next login. Hourly is ample — entries can only expire as fast as
 * two-week-old logins do.
 */
@Component
@Profile("production")
@Order(30)
@Slf4j
public class ActiveUsersItem extends DashboardItem {
  /** Rendered when the last refresh failed, so a database blip is visible rather than silent. */
  private static final long UNKNOWN = -1;

  private final AllowedUserService allowedUserService;

  private volatile long activeUsers = UNKNOWN;

  public ActiveUsersItem(AllowedUserService allowedUserService) {
    this.allowedUserService = allowedUserService;
  }

  @Override
  public int rowCount() {
    return 1;
  }

  @Override
  public void refresh() {
    try {
      activeUsers = allowedUserService.countActiveUsers();
    } catch (Exception e) {
      activeUsers = UNKNOWN;
      log.warn("Could not count active users: {}", e.getMessage(), e);
    }
  }

  @Override
  public void redraw(Terminal terminal, int startRow, int maxRows, int maxCols) {
    long count = activeUsers;
    printAt(terminal, "Active users (14d):", startRow, 1, maxRows, maxCols);
    printAt(terminal, count == UNKNOWN ? "?" : Long.toString(count), startRow, VALUE_COLUMN, maxRows, maxCols);
  }
}
