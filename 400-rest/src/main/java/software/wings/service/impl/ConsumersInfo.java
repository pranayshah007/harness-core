package software.wings.service.impl;

import io.harness.beans.ExecutionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
public class ConsumersInfo {
  int count;
  ExecutionStatus status;
  String serviceId;
  String appName;
  String appId;
  String serviceName;
}
