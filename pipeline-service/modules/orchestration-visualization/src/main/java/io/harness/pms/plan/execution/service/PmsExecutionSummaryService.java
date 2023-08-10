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

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsExecutionSummaryService {
  void regenerateStageLayoutGraph(String planExecutionId, List<NodeExecution> nodeExecutions);
  void update(String planExecutionId, Update update);
  // Saves PipelineExecutionSummaryEntity in planExecutionsSummary collection in harness-pms db
  PipelineExecutionSummaryEntity save(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity);

  /**
   * This method is used to query pipelineExecutionSummaryEntity using planExecutionId and the fields that should be set
   * in the response
   *
   * Uses- planExecutionId index
   *
   * @param planExecutionId
   * @param fields
   * @return
   */
  PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(String planExecutionId, Set<String> fields);

  /**
   * updates the top graph based on the type of nodeExecution
   * @param planExecutionId
   * @param nodeExecution
   * @param update
   * @return
   */
  boolean handleNodeExecutionUpdateFromGraphUpdate(String planExecutionId, NodeExecution nodeExecution, Update update);

  /**
   * Fetches pipeline execution ids only as an iterator from analytics node
   * Uses - accountId_organizationId_projectId_pipelineId idx
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param pipelineIdentifier
   * @return
   */
  CloseableIterator<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsFromAnalytics(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  /**
   * Delete all PipelineExecutionSummaryEntity for given planExecutionIds
   * Uses - planExecutionId_idx index
   * @param planExecutionIds
   */
  void deleteAllSummaryForGivenPlanExecutionIds(Set<String> planExecutionIds);

  /**
   * Updates TTL all PipelineExecutionSummaryEntity and its related metadata
   * @param planExecutionId Id of to be updated TTL planExecutions
   * Uses - planExecutionId_unique idx
   */
  void updateTTL(String planExecutionId, Date ttlDate);
}
