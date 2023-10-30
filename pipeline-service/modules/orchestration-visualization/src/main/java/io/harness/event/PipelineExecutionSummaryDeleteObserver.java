/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.execution.PlanExecution;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
// Its a syncObserver to delete planExecutionSummary for given planExecutions
public class PipelineExecutionSummaryDeleteObserver implements PlanExecutionDeleteObserver {
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  @Override
  public void onPlanExecutionsDelete(
      List<PlanExecution> planExecutionList, boolean retainPipelineExecutionDetailsAfterDelete) {
    if (!retainPipelineExecutionDetailsAfterDelete) {
      Set<String> planExecutionIds = planExecutionList.stream().map(PlanExecution::getUuid).collect(Collectors.toSet());

      // Delete all summary for given planExecutionIds
      pmsExecutionSummaryService.deleteAllSummaryForGivenPlanExecutionIds(planExecutionIds);
    }
  }
}
