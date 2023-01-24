package io.harness.delegate.task.googlefunctionbeans.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.logging.CommandExecutionStatus;

@OwnedBy(HarnessTeam.CDP)
public interface GoogleFunctionCommandResponse extends DelegateTaskNotifyResponseData {
  CommandExecutionStatus getCommandExecutionStatus();
  String getErrorMessage();
  UnitProgressData getUnitProgressData();
  void setCommandUnitsProgress(UnitProgressData unitProgressData);
  GoogleFunction getFunction();
}
