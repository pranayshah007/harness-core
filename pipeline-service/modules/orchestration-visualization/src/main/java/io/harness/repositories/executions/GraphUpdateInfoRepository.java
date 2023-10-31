/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PIPELINE)
public interface GraphUpdateInfoRepository
    extends PagingAndSortingRepository<GraphUpdateInfo, String>, GraphUpdateInfoRepositoryCustom {
  Optional<GraphUpdateInfo> findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
      String planExecutionId, StepCategory stepCategory);

  Optional<GraphUpdateInfo> findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategoryAndNodeExecutionId(
      String planExecutionId, StepCategory stepCategory, String nodeExecutionId);

  void deleteAllByPlanExecutionIdIn(Set<String> planExecutionIds);
}
