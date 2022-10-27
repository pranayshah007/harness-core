package io.harness.delegate.task.terraform.cleanup;

import io.harness.delegate.task.TaskParameters;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NoopTaskCleanupParameters implements TaskParameters {
  String dummyUuid;
}
