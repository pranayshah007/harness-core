package io.harness.delegate;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.TaskParameters;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskDetailsV2 {
  Class<? extends TaskParameters> taskRequest;
  Class<? extends DelegateResponseData> taskResponse;
  boolean unsupported;
}
