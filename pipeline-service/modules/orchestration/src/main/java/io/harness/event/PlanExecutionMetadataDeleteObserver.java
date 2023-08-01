/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.execution.PlanExecution;

import com.google.inject.Inject;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OwnedBy(PIPELINE)
// Its a syncObserver to delete metadata for given planExecutions
public class PlanExecutionMetadataDeleteObserver implements PlanExecutionDeleteObserver {
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject PlanService planService;

  @Override
  public void onPlanExecutionsExpiryUpdate(List<PlanExecution> planExecutionList, Date ttlDate) {
    Set<String> planIds = new HashSet<>();
    Set<String> planExecutionIds = new HashSet<>();

    for (PlanExecution planExecution : planExecutionList) {
      planExecutionIds.add(planExecution.getUuid());
      planIds.add(planExecution.getPlanId());
    }

    // update ttl for all plans
    planService.updateTTLForPlans(planIds, ttlDate);
    // Updates TTL for all planExecutionMetadata for given planExecutionIds
    planExecutionMetadataService.updateTTL(planExecutionIds, ttlDate);
  }
}
