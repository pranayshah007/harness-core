package io.harness.cdng.gitops.syncstep;

public final class SyncStepConstants {
    public static final String SYNC_RETRY_STRATEGY_DURATION_REGEX = "/^([\\d\\.]+[HMS])+$/i";
    public static final long STOP_BEFORE_STEP_TIMEOUT_SECS = 5;
    public static final long POLLER_SLEEP_SECS = 5;
}
