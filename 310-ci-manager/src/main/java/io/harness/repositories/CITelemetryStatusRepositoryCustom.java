package io.harness.repositories;

public interface CITelemetryStatusRepositoryCustom {
  boolean updateTimestampIfOlderThan(String accountId, long olderThan, long updateToTime);
}
