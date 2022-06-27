package io.harness.repositories;

public interface CITelemetryStatusRepositoryCustom {
    boolean updateTimestampIfOlderThan(String accountId, long notOlderThan, long updateToTime);
}
