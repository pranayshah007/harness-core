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
import io.harness.beans.Scope;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
public interface DeploymentStagePlanCreationInfoService {
  /**
   * Save deployment stage plan creation info.
   *
   * @param deploymentStagePlanCreationInfo the deployment stage plan creation info
   * @return deployment stage plan creation info
   */
  DeploymentStagePlanCreationInfo save(@Valid @NotNull DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfo);

  /**
   * Lists summary of deployment stages available at plan creation filtered by stage identifiers
   *
   * @param scope scope of the deployment stage
   * @param planExecutionId plan executionId of the stage execution
   * @param stageIdentifiers list of CD stage identifiers to provide the summary for
   *
   * @return a map of stage identifiers and the formatted summary if plan creation info is present
   */
  Map<String, CDStageSummaryResponseDTO> listStagePlanCreationFormattedSummaryByStageIdentifiers(
      @Valid @NotNull Scope scope, @NotNull String planExecutionId, @NotNull List<String> stageIdentifiers);
}
