package io.harness.beans;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName("DelegateHeartbeatResponseStreaming")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.PROPERTY)
public class DelegateHeartbeatResponseStreaming {
  String delegateId;
  String status;
  boolean useCdn;
  String jreVersion;
  String delegateRandomToken;
  String sequenceNumber;
  long responseSentAt;
}
