/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.response;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.delegate.cdng.execution.StepInstanceInfo;
import io.harness.delegate.cdng.execution.TasStepInstanceInfo;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.expression.Expression;
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
public class CfRollingDeployResponseNG implements CfCommandResponseNG {
  TasArtifactConfig tasArtifactConfig;
  @Expression(ALLOW_SECRETS) List<String> routeMaps;
  @NonFinal DelegateMetaInfo delegateMetaInfo;
  @NonFinal UnitProgressData unitProgressData;
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;
  private TasApplicationInfo currentProdInfo;
  private TasApplicationInfo newApplicationInfo;
  @Expression(ALLOW_SECRETS) PcfManifestsPackage pcfManifestsPackage;
  boolean deploymentStarted;
  private List<CfInternalInstanceElement> newAppInstances;

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
    return StepExecutionInstanceInfo.builder()
        .serviceInstancesBefore(convertTasApplicationToTasStepInstanceInfo(this.currentProdInfo))
        .deployedServiceInstances(convertTasApplicationToTasStepInstanceInfo(
            filterNewTasApplication(this.currentProdInfo, this.newApplicationInfo)))
        .serviceInstancesAfter(convertTasApplicationToTasStepInstanceInfo(this.newApplicationInfo))
        .build();
  }

  private TasApplicationInfo filterNewTasApplication(
      TasApplicationInfo currentProdInfo, TasApplicationInfo newApplicationInfo) {
    if (isNull(currentProdInfo)) {
      return newApplicationInfo;
    }
    if (isNull(newApplicationInfo)
        || currentProdInfo.getApplicationGuid().equals(newApplicationInfo.getApplicationGuid())) {
      return null;
    }
    return newApplicationInfo;
  }

  private List<StepInstanceInfo> convertTasApplicationToTasStepInstanceInfo(TasApplicationInfo tasApplicationInfo) {
    if (isNull(tasApplicationInfo)) {
      return Collections.emptyList();
    }
    return List.of(TasStepInstanceInfo.builder().applicationGuid(tasApplicationInfo.getApplicationGuid()).build());
  }
}
