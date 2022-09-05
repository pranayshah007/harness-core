package io.harness.delegate.task.ecs.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

@OwnedBy(HarnessTeam.CDP)
public interface EcsCommandResponse extends DelegateTaskNotifyResponseData {
  CommandExecutionStatus getCommandExecutionStatus();
  String getErrorMessage();
  UnitProgressData getUnitProgressData();
  void setCommandUnitsProgress(UnitProgressData unitProgressData);
}
