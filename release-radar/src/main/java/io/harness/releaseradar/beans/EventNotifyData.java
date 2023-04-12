package io.harness.releaseradar.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventNotifyData {
  Environment environment;
  EventType eventType;
  String buildVersion;
  String release;
  String serviceName;
}
