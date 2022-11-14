package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfInstanceSyncRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG pcfCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  PcfInfraConfig pcfInfraConfig;
  Integer timeoutIntervalInMin;
  String applicationName;
}
