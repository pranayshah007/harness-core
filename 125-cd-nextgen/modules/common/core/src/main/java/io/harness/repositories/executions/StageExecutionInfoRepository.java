/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.StageExecutionBasicSummaryProjection;
import io.harness.cdng.execution.StageExecutionInfo;

import java.util.List;
import java.util.Set;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDP)
public interface StageExecutionInfoRepository
    extends PagingAndSortingRepository<StageExecutionInfo, String>, StageExecutionInfoRepositoryCustom {
  /**
   * Finds all StageExecutionInfo for given scope and stage identifiers
   * Uses - unique_stage_execution_info_using_stage_execution_id_idx idx
   * @param accountIdentifier accountIdentifier
   * @param orgIdentifier orgIdentifier
   * @param projectIdentifier projectIdentifier
   * @param stageExecutionIds set of stage execution identifiers to filter with
   */
  List<StageExecutionBasicSummaryProjection>
  findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndStageExecutionIdIn(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Set<String> stageExecutionIds);
}
