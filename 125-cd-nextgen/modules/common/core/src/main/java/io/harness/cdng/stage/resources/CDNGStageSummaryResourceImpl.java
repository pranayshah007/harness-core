/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.stage.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.creator.plan.stage.service.DeploymentStagePlanCreationInfoService;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
public class CDNGStageSummaryResourceImpl implements CDNGStageSummaryResource {
  private final DeploymentStagePlanCreationInfoService deploymentStagePlanCreationInfoService;
  private final StageExecutionInfoService stageExecutionInfoService;

  @Inject
  public CDNGStageSummaryResourceImpl(DeploymentStagePlanCreationInfoService deploymentStagePlanCreationInfoService,
      StageExecutionInfoService stageExecutionInfoService) {
    this.deploymentStagePlanCreationInfoService = deploymentStagePlanCreationInfoService;
    this.stageExecutionInfoService = stageExecutionInfoService;
  }

  @Override
  public ResponseDTO<Map<String, CDStageSummaryResponseDTO>> listStageExecutionFormattedSummary(
      @NotNull String accountId, @NotNull String orgIdentifier, @NotNull String projectIdentifier,
      @NotNull @NotEmpty List<String> stageExecutionIdentifiers) {
    return ResponseDTO.newResponse(
        stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
            Scope.of(accountId, orgIdentifier, projectIdentifier), stageExecutionIdentifiers));
  }

  @Override
  public ResponseDTO<Map<String, CDStageSummaryResponseDTO>> listStagePlanCreationFormattedSummary(
      @NotNull String accountId, @NotNull String orgIdentifier, @NotNull String projectIdentifier,
      @NotNull String planExecutionId, @NotNull @NotEmpty List<String> stageIdentifiers

  ) {
    return ResponseDTO.newResponse(
        deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
            Scope.of(accountId, orgIdentifier, projectIdentifier), planExecutionId, stageIdentifiers));
  }
}
