/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.execution.PlanExecution;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineResourceRestraintInstanceDeleteObserver implements PlanExecutionDeleteObserver {
  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;

  @Override
  public void onPlanExecutionsDelete(List<PlanExecution> planExecutionList) {
    Set<String> planExecutionIds = planExecutionList.stream().map(PlanExecution::getUuid).collect(Collectors.toSet());
    // Delete all resource restraint instances with pipeline scope and deleted planExecutionIds
    resourceRestraintInstanceService.deleteInstancesForGivenReleaseType(planExecutionIds, HoldingScope.PIPELINE);
  }
}
