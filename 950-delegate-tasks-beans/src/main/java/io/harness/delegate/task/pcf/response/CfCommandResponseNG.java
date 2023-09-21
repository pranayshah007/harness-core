/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.CDDelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.logging.CommandExecutionStatus;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
public interface CfCommandResponseNG extends CDDelegateTaskNotifyResponseData {
  CommandExecutionStatus getCommandExecutionStatus();
  String getErrorMessage();
  UnitProgressData getUnitProgressData();
  void setCommandUnitsProgress(UnitProgressData unitProgressData);
  default StepExecutionInstanceInfo getStepExecutionInstanceInfo() {
    return null;
  }
}
