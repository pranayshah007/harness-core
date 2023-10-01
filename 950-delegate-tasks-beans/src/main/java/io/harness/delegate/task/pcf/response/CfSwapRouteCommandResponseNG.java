/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.response;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfSwapRouteCommandResult;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.delegate.cdng.execution.StepInstanceInfo;
import io.harness.delegate.cdng.execution.TasStepInstanceInfo;
import io.harness.logging.CommandExecutionStatus;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRouteCommandResponseNG implements CfCommandResponseNG {
  @NonFinal DelegateMetaInfo delegateMetaInfo;
  @NonFinal UnitProgressData unitProgressData;
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;
  CfSwapRouteCommandResult cfSwapRouteCommandResult;
  String newApplicationName;
  @Override
  public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
    this.delegateMetaInfo = metaInfo;
  }

  @Override
  public void setCommandUnitsProgress(UnitProgressData unitProgressData) {
    this.unitProgressData = unitProgressData;
  }

  @Override
  public StepExecutionInstanceInfo getStepExecutionInstanceInfo() {
    if (isNull(this.cfSwapRouteCommandResult)) {
      return StepExecutionInstanceInfo.builder()
          .deployedServiceInstances(Collections.emptyList())
          .serviceInstancesAfter(Collections.emptyList())
          .serviceInstancesBefore(Collections.emptyList())
          .build();
    }
    return StepExecutionInstanceInfo.builder()
        .serviceInstancesBefore(
            convertTasApplicationToTasStepInstanceInfo(this.cfSwapRouteCommandResult.getActiveApplicationInfo()))
        .deployedServiceInstances(
            convertTasApplicationToTasStepInstanceInfo(this.cfSwapRouteCommandResult.getNewApplicationInfo()))
        .serviceInstancesAfter(
            convertTasApplicationToTasStepInstanceInfo(this.cfSwapRouteCommandResult.getNewApplicationInfo()))
        .build();
  }

  private List<StepInstanceInfo> convertTasApplicationToTasStepInstanceInfo(TasApplicationInfo tasApplicationInfo) {
    if (isNull(tasApplicationInfo)) {
      return Collections.emptyList();
    }
    return List.of(TasStepInstanceInfo.builder().applicationGuid(tasApplicationInfo.getApplicationGuid()).build());
  }
}
