/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.repositories.executions.DeploymentStagePlanCreationInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  @Override
  public Map<String, CDStageSummaryResponseDTO> listStagePlanCreationFormattedSummaryByStageIdentifiers(
      @Valid @NotNull Scope scope, @NotNull String planExecutionId, @NotNull List<String> stageIdentifiers) {
    if (isBlank(planExecutionId) || isEmpty(stageIdentifiers)) {
      log.warn("Blank execution id : [{}] or empty stage identifiers : {} provided", planExecutionId, stageIdentifiers);
      throw new InvalidRequestException("Empty plan execution identifier or stage identifiers provided");
    }

    if (io.harness.encryption.Scope.PROJECT
        != io.harness.encryption.Scope.of(
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier())) {
      log.warn("scope provided is invalid, PROJECT scope expected : {}", scope);
      throw new InvalidRequestException("Invalid scope provided, project scope expected");
    }
    List<DeploymentStagePlanCreationInfo> deploymentStagePlanCreationInfoList =
        deploymentStagePlanCreationInfoRepository
            .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
                scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
                new HashSet<>(stageIdentifiers));
    Map<String, CDStageSummaryResponseDTO> deploymentStagePlanCreationInfoStageMap = new HashMap<>();
    if (isEmpty(deploymentStagePlanCreationInfoList)) {
      log.warn(
          "deploymentStagePlanCreationInfo not found for given accountId: {}, ordId: {}, projectId: {}, planExecutionId:{}, stageIds: {}",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          stageIdentifiers);
      return deploymentStagePlanCreationInfoStageMap;
    }
    return deploymentStagePlanCreationInfoList.stream().collect(
        Collectors.toMap(DeploymentStagePlanCreationInfo::getStageIdentifier,
            detailsInfo -> detailsInfo.getDeploymentStageDetailsInfo().getFormattedStageSummary()));
  }
}