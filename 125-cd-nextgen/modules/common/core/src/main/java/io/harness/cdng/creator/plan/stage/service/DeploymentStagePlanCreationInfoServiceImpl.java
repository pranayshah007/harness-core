/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.repositories.executions.DeploymentStagePlanCreationInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDC)
public class DeploymentStagePlanCreationInfoServiceImpl implements DeploymentStagePlanCreationInfoService {
  private DeploymentStagePlanCreationInfoRepository deploymentStagePlanCreationInfoRepository;
  @Override
  public DeploymentStagePlanCreationInfo save(
      @Valid @NotNull DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfo) {
    return deploymentStagePlanCreationInfoRepository.save(deploymentStagePlanCreationInfo);
  }
}