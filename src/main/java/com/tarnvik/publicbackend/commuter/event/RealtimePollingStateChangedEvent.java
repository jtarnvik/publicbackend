package com.tarnvik.publicbackend.commuter.event;

/**
 * Published when the GTFS realtime poll loop starts or stops.
 * <p>
 * The loop is request-driven — it runs only while someone is watching the live traffic view — so
 * these two transitions are the whole signal. There is no periodic "still running" event, and none
 * is needed: the state only changes at those two points.
 * <p>
 * Published from the poll loop's own virtual thread, and listeners run synchronously on it. A
 * listener must therefore not block.
 */
public record RealtimePollingStateChangedEvent(boolean active) {
}
