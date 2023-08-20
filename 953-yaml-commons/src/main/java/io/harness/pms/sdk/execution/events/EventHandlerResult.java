package io.harness.pms.sdk.execution.events;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventHandlerResult<T> {
  T data;
  boolean success;
}
