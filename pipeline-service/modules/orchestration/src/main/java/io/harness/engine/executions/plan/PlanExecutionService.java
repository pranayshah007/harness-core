/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.NonNull;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PlanExecutionService extends NodeStatusUpdateObserver {
  PlanExecution update(@NonNull String planExecutionId, @NonNull Consumer<Update> ops);

  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status);

  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops);

  PlanExecution updateStatusForceful(
      @NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops, boolean forced);

  PlanExecution get(String planExecutionId);

  PlanExecution save(PlanExecution planExecution);

  List<PlanExecution> findAllByPlanExecutionIdIn(List<String> planExecutionIds);

  List<PlanExecution> findPrevUnTerminatedPlanExecutionsByExecutionTag(
      PlanExecution planExecution, String executionTag);

  Status calculateStatus(String planExecutionId);

  PlanExecution updateCalculatedStatus(String planExecutionId);

  Status calculateStatusExcluding(String planExecutionId, String excludedNodeExecutionId);

  List<PlanExecution> findByStatusWithProjections(Set<Status> statuses, Set<String> fieldNames);

  List<PlanExecution> findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
      String accountId, String orgId, String projectId, long startTS, long endTS);
}
