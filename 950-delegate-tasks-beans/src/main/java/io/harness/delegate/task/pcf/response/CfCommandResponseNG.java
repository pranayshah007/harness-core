package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

@OwnedBy(HarnessTeam.CDP)
public interface CfCommandResponseNG extends DelegateTaskNotifyResponseData {
  CommandExecutionStatus getCommandExecutionStatus();
  String getErrorMessage();
  UnitProgressData getUnitProgressData();
  void setCommandUnitsProgress(UnitProgressData unitProgressData);
}
