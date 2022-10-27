package io.harness.delegate.task.terraform.cleanup;

import io.harness.delegate.beans.DelegateResponseData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NoopTaskCleanupResponse implements DelegateResponseData {
  String responseDataUuid;
}
