package com.tarnvik.publicbackend.commuter.port.outgoing.terminal.items;

import com.tarnvik.publicbackend.commuter.port.outgoing.terminal.DashboardItem;
import com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Whether the GTFS realtime poll loop is running.
 * <p>
 * Because the loop is request-driven and shuts down five minutes after the last request, this
 * doubles as "is anyone watching the live traffic view right now".
 * <p>
 * No {@code refresh()} override: the state is an in-memory flag, so reading it during a repaint is
 * free and cannot go stale between an event and the paint that follows it.
 */
@Component
@Profile("production")
@Order(20)
public class RealtimePollingItem extends DashboardItem {
  private final GtfsRealtimeService gtfsRealtimeService;

  public RealtimePollingItem(GtfsRealtimeService gtfsRealtimeService) {
    this.gtfsRealtimeService = gtfsRealtimeService;
  }

  @Override
  public int rowCount() {
    return 1;
  }

  @Override
  public void redraw(Terminal terminal, int startRow, int maxRows, int maxCols) {
    printAt(terminal, "Realtime polling:", startRow, 1, maxRows, maxCols);
    printOnOff(terminal, gtfsRealtimeService.isPollLoopActive(), startRow, VALUE_COLUMN, maxRows, maxCols);
  }
}
