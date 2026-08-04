package com.tarnvik.publicbackend.commuter.port.outgoing.terminal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.tarnvik.publicbackend.commuter.event.RealtimePollingStateChangedEvent;
import com.tarnvik.publicbackend.commuter.event.UserActivityEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Curses;
import org.jline.utils.InfoCmp;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A fixed status board drawn on the terminal the backend was started from.
 * <p>
 * <strong>One thread owns the screen.</strong> Every terminal write happens on a single render
 * thread. Listeners and signal handlers do not draw — they set a flag and wake it. That is what
 * makes {@code printAt}'s cursor-address-then-write pair safe without locking: two threads
 * interleaving those two calls would print each other's text at each other's coordinates, which is
 * exactly the kind of fault that only shows up when two events happen to coincide.
 * <p>
 * <strong>It is not a polling loop.</strong> The render thread blocks on {@link #wakeSignal} and
 * consumes nothing until an event arrives. A burst of events collapses into one repaint, because
 * the queue holds a single token.
 * <p>
 * <strong>Console logging is detached</strong> once the board is up, otherwise log lines would
 * overwrite it. It stays attached during startup on purpose — a deployment that fails before
 * {@link ApplicationReadyEvent} prints its diagnostics to the terminal as usual, and because the
 * board lives in the alternate screen buffer, that startup output is still there after exit.
 * <p>
 * Active under the {@code production} profile only. Elsewhere the bean does not exist and logging
 * behaves normally.
 */
@Component
@Profile("production")
@Slf4j
public class DashboardService {
  /** Any token; the queue is a wake-up signal, not a carrier of data. */
  private static final Object WAKE = new Object();

  private final List<DashboardItem> items;
  private final ConfigurableApplicationContext applicationContext;

  /** Capacity 1: a second pending wake-up would be redundant, so it is dropped. */
  private final BlockingQueue<Object> wakeSignal = new ArrayBlockingQueue<>(1);
  private final AtomicBoolean refreshNeeded = new AtomicBoolean(false);

  private Terminal terminal;
  private Thread renderThread;
  private volatile boolean running;
  private volatile int rows;
  private volatile int cols;

  /**
   * The escape sequence that puts the terminal back the way it was found, resolved from terminfo at
   * startup and replayed at shutdown.
   * <p>
   * Captured eagerly and written straight to stdout rather than through {@link #terminal}, because
   * by the time bean destruction runs the jline terminal has already been closed and every write
   * through it throws "Terminal has been closed". Stdout is still open at that point. Getting this
   * wrong leaves the user's shell on the alternate screen with an invisible cursor, which survives
   * the process and needs a manual {@code reset}.
   */
  private String restoreSequence = "";

  /** Items arrive ordered by their {@code @Order} annotation, which fixes their order on screen. */
  public DashboardService(List<DashboardItem> items, ConfigurableApplicationContext applicationContext) {
    this.items = items;
    this.applicationContext = applicationContext;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    try {
      terminal = TerminalBuilder.builder()
        .system(true)
        // jline 4 probes the terminal at build time (DECRQM / DA1 / DSR-CPR) to auto-detect
        // grapheme-cluster support. Those replies leak into the display because the board does not
        // run in raw mode. It renders styled ASCII only, so the probe is pure downside.
        .graphemeCluster(false)
        .build();
    } catch (IOException e) {
      log.warn("Terminal dashboard disabled — could not open a terminal: {}", e.getMessage());
      return;
    }

    if (!isUsableTerminal()) {
      log.info("Terminal dashboard disabled — no usable terminal (type={}, columns={}). "
        + "This is expected when stdout is redirected or piped.",
        terminal.getType(), terminal.getSize().getColumns());
      closeTerminalQuietly();
      terminal = null;
      return;
    }

    rows = terminal.getSize().getRows();
    cols = terminal.getSize().getColumns();
    log.info("Terminal dashboard starting — type={}, size={}x{}", terminal.getType(), cols, rows);

    restoreSequence = resolveRestoreSequence();

    // Last thing written to the console: after this the board owns the screen.
    detachConsoleAppender();

    terminal.puts(InfoCmp.Capability.enter_ca_mode);
    terminal.puts(InfoCmp.Capability.clear_screen);
    terminal.puts(InfoCmp.Capability.cursor_home);
    terminal.puts(InfoCmp.Capability.cursor_invisible);
    terminal.handle(Terminal.Signal.WINCH, signal -> onResize());
    terminal.handle(Terminal.Signal.INT, signal -> onInterrupt());

    running = true;
    renderThread = Thread.ofVirtual().name("dashboard-render").start(this::renderLoop);
    requestRedraw(true);
  }

  /**
   * A terminal is usable when it can address a cursor and has a width. A piped or redirected stdout
   * reports type {@code dumb} and/or zero columns — the board then stays off rather than emitting
   * escape sequences into a log file.
   */
  private boolean isUsableTerminal() {
    return !Terminal.TYPE_DUMB.equals(terminal.getType())
      && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType())
      && terminal.getSize().getColumns() > 0;
  }

  /**
   * Wakes the render thread.
   *
   * @param withRefresh whether item data may have changed. False is a bare repaint — a resize —
   *                    and deliberately skips {@link DashboardItem#refresh()} so that dragging a
   *                    window does not issue a database query per redraw.
   */
  public void requestRedraw(boolean withRefresh) {
    if (withRefresh) {
      refreshNeeded.set(true);
    }
    wakeSignal.offer(WAKE);
  }

  @EventListener
  public void onRealtimePollingStateChanged(RealtimePollingStateChangedEvent event) {
    requestRedraw(true);
  }

  /**
   * After commit, not before: the listener's repaint recounts active users, and inside the
   * publishing transaction that count could still be reading pre-commit state.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserActivity(UserActivityEvent event) {
    requestRedraw(true);
  }

  /**
   * Ctrl-C.
   * <p>
   * This handler is not optional. Building a jline <em>system</em> terminal installs native signal
   * handlers, which take SIGINT away from the JVM — verified: without this, Ctrl-C kills the
   * process with no shutdown hook, no bean destruction and therefore no terminal restore, leaving
   * the shell on the alternate screen with an invisible cursor. Closing the context explicitly
   * runs {@link #cleanup()} the same way an orderly shutdown would.
   */
  private void onInterrupt() {
    log.info("Interrupt received — shutting down");
    applicationContext.close();
    System.exit(0);
  }

  private void onResize() {
    rows = terminal.getSize().getRows();
    cols = terminal.getSize().getColumns();
    requestRedraw(false);
  }

  private void renderLoop() {
    while (running) {
      try {
        wakeSignal.take();
        if (!running) {
          return;
        }
        if (refreshNeeded.getAndSet(false)) {
          refreshItems();
        }
        drawAll();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        // Never let a rendering fault kill the thread — that would freeze the board silently.
        log.warn("Dashboard redraw failed: {}", e.getMessage(), e);
      }
    }
  }

  private void refreshItems() {
    for (DashboardItem item : items) {
      try {
        item.refresh();
      } catch (Exception e) {
        log.warn("Dashboard item {} failed to refresh: {}", item.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  private void drawAll() {
    terminal.puts(InfoCmp.Capability.clear_screen);
    int row = 1;
    for (DashboardItem item : items) {
      try {
        item.redraw(terminal, row, rows, cols);
      } catch (Exception e) {
        log.warn("Dashboard item {} failed to draw: {}", item.getClass().getSimpleName(), e.getMessage(), e);
      }
      row += item.rowCount();
    }
    terminal.flush();
  }

  /**
   * Removes the CONSOLE appender declared in {@code logback-spring.xml}, so log lines stop
   * overwriting the board. Guarded by an {@code instanceof} rather than assuming logback: if the
   * logging backend is ever swapped, this degrades to leaving logging alone instead of failing.
   */
  private void detachConsoleAppender() {
    ILoggerFactory factory = LoggerFactory.getILoggerFactory();
    if (!(factory instanceof LoggerContext context)) {
      log.warn("Dashboard could not detach console logging — backend is not logback ({}). "
        + "Log output will overwrite the dashboard.", factory.getClass().getName());
      return;
    }
    Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    Appender<ILoggingEvent> console = rootLogger.getAppender("CONSOLE");
    if (console == null) {
      // Expected off-production, where no CONSOLE appender is attached by name.
      return;
    }
    rootLogger.detachAppender(console);
    console.stop();
  }

  /**
   * Expands the terminfo capabilities that show the cursor and leave the alternate screen, while
   * the terminal is open and can still be queried. Falls back to the xterm-family defaults if the
   * terminal reports no capability, which is better than leaving the screen unrecoverable.
   */
  private String resolveRestoreSequence() {
    return expandCapability(InfoCmp.Capability.cursor_normal, "\033[?25h")
      + expandCapability(InfoCmp.Capability.exit_ca_mode, "\033[?1049l");
  }

  private String expandCapability(InfoCmp.Capability capability, String fallback) {
    String terminfo = terminal.getStringCapability(capability);
    if (terminfo == null) {
      return fallback;
    }
    return Curses.tputs(terminfo);
  }

  /**
   * Restores the terminal. Reached from {@link #onInterrupt()} on Ctrl-C, and from Spring's
   * shutdown hook on SIGTERM or any orderly stop.
   * <p>
   * The restore is written to stdout directly, not through the jline terminal: bean destruction can
   * run late enough that the terminal is already closed, and writing through it then throws
   * "Terminal has been closed" — which is how this was found.
   */
  @PreDestroy
  public void cleanup() {
    running = false;
    if (renderThread != null) {
      renderThread.interrupt();
    }
    if (restoreSequence.isEmpty()) {
      return;
    }
    System.out.print(restoreSequence);
    System.out.flush();
  }

  /** Used only for a terminal that was opened but turned out to be unusable. */
  private void closeTerminalQuietly() {
    try {
      terminal.close();
    } catch (IOException e) {
      System.err.println("Failed to close terminal: " + e.getMessage());
    }
  }
}
