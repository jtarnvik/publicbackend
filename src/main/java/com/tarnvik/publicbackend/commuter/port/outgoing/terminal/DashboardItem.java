package com.tarnvik.publicbackend.commuter.port.outgoing.terminal;

import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

/**
 * One block of lines on the terminal dashboard.
 * <p>
 * Items never decide where they sit: {@link DashboardService} assigns each a start row by summing
 * the {@link #rowCount()} of everything before it, so inserting an item cannot require editing its
 * neighbours. All coordinates passed to {@code printAt} are absolute, one-based.
 * <p>
 * <strong>Threading.</strong> Both {@link #refresh()} and {@link #redraw} are called only from the
 * single render thread, so implementations need no synchronisation and the terminal is never
 * written to concurrently.
 * <p>
 * <strong>{@code refresh()} versus {@code redraw()}.</strong> Anything expensive — a database
 * query, an upstream call — belongs in {@code refresh()}, which runs only when an event says the
 * underlying data may have changed. {@code redraw()} also runs on a bare repaint such as a terminal
 * resize, so it must be cheap: paint from a cached field, or read a value that is genuinely free
 * (an in-memory flag).
 */
@Slf4j
public abstract class DashboardItem {
  /** Column every item starts its value in, so labels and values line up down the board. */
  protected static final int VALUE_COLUMN = 22;

  /** How many terminal rows this item occupies, including any trailing blank line it wants. */
  public abstract int rowCount();

  /**
   * Recomputes cached state. Called before a repaint that was triggered by a data event, never on
   * a bare resize. Exceptions are caught and logged by the caller; an item that fails here should
   * leave its previous value in place or set a sentinel it can render as unknown.
   */
  public void refresh() {
    // Nothing to recompute by default.
  }

  /** Paints this item. Must not perform I/O — see the class javadoc. */
  public abstract void redraw(Terminal terminal, int startRow, int maxRows, int maxCols);

  /**
   * Clips text to what actually fits, returning empty when the anchor itself is off screen. A small
   * terminal therefore loses the bottom of the dashboard rather than wrapping it into nonsense.
   */
  private String truncateToMax(String text, int atRow, int atCol, int maxRows, int maxCols) {
    if (atRow > maxRows || atCol > maxCols) {
      return "";
    }
    int maxCharsAllowed = maxCols - atCol + 1;
    if (maxCharsAllowed >= text.length()) {
      return text;
    }
    return text.substring(0, maxCharsAllowed);
  }

  protected void printAt(Terminal terminal, String text, int atRow, int atCol, int maxRows, int maxCols) {
    String allowedToPrint = truncateToMax(text, atRow, atCol, maxRows, maxCols);
    if (allowedToPrint.isEmpty()) {
      return;
    }
    terminal.puts(InfoCmp.Capability.cursor_address, atRow - 1, atCol - 1);
    terminal.writer().print(allowedToPrint);
  }

  protected void printAt(Terminal terminal, String text, int atRow, int atCol, int maxRows, int maxCols,
                         int fg, int bg, boolean bold) {
    String allowedToPrint = truncateToMax(text, atRow, atCol, maxRows, maxCols);
    if (allowedToPrint.isEmpty()) {
      return;
    }
    terminal.puts(InfoCmp.Capability.cursor_address, atRow - 1, atCol - 1);
    AttributedStyle style = AttributedStyle.DEFAULT.foreground(fg).background(bg);
    if (bold) {
      style = style.bold();
    }
    terminal.writer().print(new AttributedString(allowedToPrint, style).toAnsi());
  }

  /** Renders a state flag the same way everywhere on the board. */
  protected void printOnOff(Terminal terminal, boolean on, int atRow, int atCol, int maxRows, int maxCols) {
    if (on) {
      printAt(terminal, " ON  ", atRow, atCol, maxRows, maxCols,
        AttributedStyle.BLACK, AttributedStyle.GREEN, true);
    } else {
      printAt(terminal, " OFF ", atRow, atCol, maxRows, maxCols,
        AttributedStyle.BLACK, AttributedStyle.RED, true);
    }
  }
}
