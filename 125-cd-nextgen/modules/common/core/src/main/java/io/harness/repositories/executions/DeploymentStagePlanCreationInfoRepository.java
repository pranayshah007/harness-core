/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;

import java.util.List;
import java.util.Set;
import org.springframework.data.repository.PagingAndSortingRepository;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@HarnessRepo
@OwnedBy(CDC)
public interface DeploymentStagePlanCreationInfoRepository
    extends PagingAndSortingRepository<DeploymentStagePlanCreationInfo, String> {
  /**
   * Finds all DeploymentStagePlanCreationInfo for given scope, plan execution id and stage identifiers
   * Uses - unique_deployment_stage_plan_creation_info_using_plan_execution_id_stage_id_idx idx
   * or deployment_stage_plan_creation_info_using_plan_execution_id_idx idx
   * @param accountIdentifier accountIdentifier
   * @param orgIdentifier orgIdentifier
   * @param projectIdentifier projectIdentifier
   * @param planExecutionId planExecutionId
   * @param stageIdentifiers set of stage identifiers to filter with
   */
  List<DeploymentStagePlanCreationInfo>
  findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String planExecutionId,
      Set<String> stageIdentifiers);
}
