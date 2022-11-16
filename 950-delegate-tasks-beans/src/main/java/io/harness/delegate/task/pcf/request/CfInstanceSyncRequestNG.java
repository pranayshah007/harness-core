package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfInstanceSyncRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  TasInfraConfig tasInfraConfig;
  Integer timeoutIntervalInMin;
  String applicationName;
}
