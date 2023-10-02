/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PmsExecutionSummaryRepositoryCustom {
  PipelineExecutionSummaryEntity update(Query query, Update update);

  // updates multiple records and doesnt return any record
  void multiUpdate(Query query, Update update);
  UpdateResult deleteAllExecutionsWhenPipelineDeleted(Query query, Update update);
  Page<PipelineExecutionSummaryEntity> findAll(Criteria criteria, Pageable pageable);

  Page<PipelineExecutionSummaryEntity> findAllWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections);

  CloseableIterator<PipelineExecutionSummaryEntity> findAllWithRequiredProjectionUsingAnalyticsNode(
      Criteria criteria, Pageable pageable, List<String> projections);

  long getCountOfExecutionSummary(Criteria criteria);
  String fetchRootRetryExecutionId(String planExecutionId);

  CloseableIterator<PipelineExecutionSummaryEntity> findListOfBranches(Criteria criteria);

  CloseableIterator<PipelineExecutionSummaryEntity> findListOfRepositories(Criteria criteria);

  /**
   * Returns iterator on PipelineExecutionSummaryEntity for given query having projection fields else throws exception
   * The results are fetched from analytics peferred db node
   * @param query
   * @return
   */
  CloseableIterator<PipelineExecutionSummaryEntity> fetchExecutionSummaryEntityFromAnalytics(Query query);

  /**
   * Fetches PipelineExecutionSummaryEntity from DB using projections.
   * Only fields specified in fieldsToInclude are added.
   * @param criteria
   * @param fieldsToInclude
   * @return
   */
  PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      Criteria criteria, Set<String> fieldsToInclude);

  /**
   * Fetches pipeline execution summary entity from rootParentId. Used to calculate the last retried pipeline
   *
   * Uses: rootExecution_createdAt_id idx
   *
   * @param rootParentId
   * @return
   */
  CloseableIterator<PipelineExecutionSummaryEntity> fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
      String rootParentId);
}
