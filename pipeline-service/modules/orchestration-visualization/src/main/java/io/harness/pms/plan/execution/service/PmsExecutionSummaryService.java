/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.NodeExecution;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsExecutionSummaryService {
  void regenerateStageLayoutGraph(String planExecutionId, List<NodeExecution> nodeExecutions);
  void updateEndTs(String planExecutionId, NodeExecution nodeExecution);
  void update(String planExecutionId, Update update);
  void updateStageOfIdentityType(String planExecutionId, Update update);
  void addStageNodeInGraphIfUnderStrategy(String planExecutionId, NodeExecution nodeExecution, Update update);
  void updateStrategyNode(String planExecutionId, NodeExecution nodeExecution, Update update);
  Optional<PipelineExecutionSummaryEntity> getPipelineExecutionSummary(
      String accountId, String orgId, String projectId, String planExecutionId);
}
