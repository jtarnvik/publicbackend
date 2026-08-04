package com.tarnvik.publicbackend.commuter.event;

/**
 * Published when a user's activity timestamp has been refreshed.
 * <p>
 * Fires from {@code AllowedUserService.recordLogin()}, which runs on every {@code GET /api/auth/me}
 * — i.e. every time the frontend is opened, not only on OAuth2 sign-in. "Activity" here therefore
 * means "opened the app", which is the useful sense for an active-user count.
 * <p>
 * Published inside a transaction. Listeners that read the resulting count must use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} or they may observe pre-commit state.
 */
public record UserActivityEvent(String email) {
}
