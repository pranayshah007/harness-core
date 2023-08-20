package io.harness.ng.core.event;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HandleResult {
  String messageId;
  boolean ack;
}
