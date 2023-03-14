package io.harness.cdng.gitops.syncstep;

public enum SyncOperationPhase {
    RUNNING("Running"),
    FAILED("Failed"),
    ERROR("Error"),
    SUCCEEDED("Succeeded"),
    TERMINATING("Terminating");

    private final String value;

    SyncOperationPhase(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
