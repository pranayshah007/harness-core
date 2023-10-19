/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.execution.PlanExecution;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.NonNull;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PlanExecutionService extends NodeStatusUpdateObserver {
  PlanExecution updateStatusForceful(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops,
      boolean forced, EnumSet<Status> allowedStartStatuses);
  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status);
  PlanExecution markPlanExecutionErrored(String planExecutionId);
  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops);

  PlanExecution updateStatusForceful(
      @NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops, boolean forced);

  PlanExecution get(String planExecutionId);
  PlanExecution getWithFieldsIncluded(String planExecutionId, Set<String> fieldsToInclude);

  /**
   * @param planExecutionId planExecutionId
   * @return This method returns PlanExecution but excluding ExecutionMetadata, GovernanceMetadata,
   * setupAbstractions and ambiance
   */
  PlanExecution getPlanExecutionMetadata(String planExecutionId);

  ExecutionMetadata getExecutionMetadataFromPlanExecution(String planExecutionId);

  PlanExecution save(PlanExecution planExecution);

  Status getStatus(String planExecutionId);

  List<PlanExecution> findAllByPlanExecutionIdIn(List<String> planExecutionIds);

  List<PlanExecution> findPrevUnTerminatedPlanExecutionsByExecutionTag(
      PlanExecution planExecution, String executionTag);

  Status calculateStatus(String planExecutionId);

  Status calculateStatus(String planExecutionId, boolean shouldSkipIdentityNodes);

  PlanExecution updateCalculatedStatus(String planExecutionId);

  /**
   * Updated planExecution status if calculated status are non-final and non-flowing statuses under Lock
   * @param planExecutionId
   * @param excludeNodeExecutionStatus
   */
  void calculateAndUpdateRunningStatusUnderLock(String planExecutionId, Status excludeNodeExecutionStatus);

  List<PlanExecution> findByStatusWithProjections(Set<Status> statuses, Set<String> fieldNames);

  /**
   * Fetches all planExecutions entries with given status with fieldsToBeIncluded as projections from analytics node
   * Uses - status_idx index
   * @param statuses
   * @param fieldNames
   * @return
   */
  CloseableIterator<PlanExecution> fetchPlanExecutionsByStatusFromAnalytics(
      Set<Status> statuses, Set<String> fieldNames);

  // Todo: Remove
  List<PlanExecution> findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  long countRunningExecutionsForGivenPipelineInAccount(String accountId, String pipelineIdentifier);

  long countRunningExecutionsForGivenPipelineInAccountExcludingWaitingStatuses(
      String accountId, String pipelineIdentifier);

  PlanExecution findNextExecutionToRunInAccount(String accountId);

  /**
   * Deletes the planExecution and its related metadata
   * @param planExecutionIds Ids of to be deleted planExecutions
   */
  void deleteAllPlanExecutionAndMetadata(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete);

  /**
   * Updates TTL all planExecution and its related metadata
   * @param planExecutionId Ids of to be updated TTL planExecutions
   */
  void updateTTL(String planExecutionId, Date ttlDate);

  /**
   * Fetches aggregated running execution count per account from analytics node
   * @return
   */
  List<ExecutionCountWithAccountResult> aggregateRunningExecutionCountPerAccount();
}
