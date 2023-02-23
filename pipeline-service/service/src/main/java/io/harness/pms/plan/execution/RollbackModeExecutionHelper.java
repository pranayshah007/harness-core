/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  private final NodeExecutionService nodeExecutionService;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  private final PMSPipelineService pmsPipelineService;
  private final PMSExecutionService pmsExecutionService;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;

  public Plan transformPlanForRollbackMode(
      PlanCreationBlobResponse planCreationResponse, String previousExecutionId, String pipelineYaml) {
    Plan plan = PlanExecutionUtils.extractPlan(planCreationResponse);
    List<Node> planNodes = plan.getPlanNodes();
    return null;
  }
}
