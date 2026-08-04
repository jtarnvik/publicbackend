package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.port.outgoing.terminal.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The dashboard's only clock.
 * <p>
 * Everything else on the board repaints in response to an event. The active-user count is the
 * exception: a user drops out of the window purely because time passed, and no code runs at that
 * moment to announce it. This job is that missing signal — not a polling loop, and deliberately
 * hourly rather than frequent, since entries can only expire as fast as two-week-old logins do.
 * <p>
 * Cheap enough not to matter to the shared scheduler: it sets a flag and offers a token to a queue.
 */
@Component
@Profile("production")
@RequiredArgsConstructor
public class DashboardRefreshJob {
  private final DashboardService dashboardService;

  @Scheduled(cron = "0 0 * * * *")
  public void refreshDashboard() {
    dashboardService.requestRedraw(true);
  }
}
