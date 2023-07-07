package io.harness.ngtriggers.beans.dto.eventmapping;

import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotMatchedTriggerInfo {
  TriggerDetails notMatchedTriggers;
  FinalStatus finalStatus;
  String message;
}
