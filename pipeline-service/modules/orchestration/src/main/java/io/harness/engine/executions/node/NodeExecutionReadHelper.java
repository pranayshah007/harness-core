/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.mongo.helper.AnalyticsMongoTemplateHolder;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.monitoring.ExecutionCountWithAccountResult.ExecutionCountWithAccountResultKeys;
import io.harness.monitoring.ExecutionCountWithModuleResult.ExecutionCountWithModuleResultKeys;
import io.harness.monitoring.ExecutionCountWithStepTypeResult.ExecutionCountWithStepTypeResultKeys;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.monitoring.ExecutionStatistics.ExecutionStatisticsKeys;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class NodeExecutionReadHelper {
  private static final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate analyticsMongoTemplate;
  @Inject
  public NodeExecutionReadHelper(MongoTemplate mongoTemplate, AnalyticsMongoTemplateHolder analyticsMongoTemplateHolder,
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.analyticsMongoTemplate = analyticsMongoTemplateHolder.getAnalyticsMongoTemplate();
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Deprecated
  /**
   * @deprecated Use getOne below method which checks for projection field necessary
   */
  public Optional<NodeExecution> getOneWithoutProjections(Query query) {
    return Optional.ofNullable(mongoTemplate.findOne(query, NodeExecution.class));
  }

  public Optional<NodeExecution> getOne(Query query) {
    validateNodeExecutionProjection(query);
    return Optional.ofNullable(mongoTemplate.findOne(query, NodeExecution.class));
  }

  public CloseableIterator<NodeExecution> fetchNodeExecutions(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validateNodeExecutionStreamQuery(query);
    return mongoTemplate.stream(query, NodeExecution.class);
  }

  public CloseableIterator<NodeExecution> fetchNodeExecutionsWithAllFields(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    return mongoTemplate.stream(query, NodeExecution.class);
  }

  public CloseableIterator<NodeExecution> fetchNodeExecutionsFromAnalytics(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validateNodeExecutionStreamQuery(query);
    return analyticsMongoTemplate.stream(query, NodeExecution.class);
  }

  /**
   * Should be used only for nodeExecutionReads where there is no projection
   * Get approval before using this method
   */
  public CloseableIterator<NodeExecution> fetchNodeExecutionsIteratorWithoutProjections(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    return mongoTemplate.stream(query, NodeExecution.class);
  }

  // Get count from primary node
  public long findCount(Query query) {
    return mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NodeExecution.class);
  }

  /**
   * Should be used only for nodeExecutionReads where there is no projection
   * Get approval before using this method
   */
  public List<NodeExecution> fetchNodeExecutionsWithoutProjections(Query query) {
    return mongoTemplate.find(query, NodeExecution.class);
  }

  private void validateNodeExecutionStreamQuery(Query query) {
    if (query.getMeta().getCursorBatchSize() == null || query.getMeta().getCursorBatchSize() <= 0
        || query.getMeta().getCursorBatchSize() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "NodeExecution query should have cursorBatch limit within max batch size- " + MAX_BATCH_SIZE);
    }
    validateNodeExecutionProjection(query);
  }

  private void validateNodeExecutionProjection(Query query) {
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("NodeExecution list query should have projection fields");
    }
  }

  public NodeExecution fetchNodeExecutionsFromSecondaryTemplate(Query query) {
    validateNodeExecutionProjection(query);
    return secondaryMongoTemplate.findOne(query, NodeExecution.class);
  }

  public ExecutionStatistics aggregateRunningExecutionCount() {
    MatchOperation matchStage =
        Aggregation.match(Criteria.where(NodeExecutionKeys.status).in(StatusUtils.activeStatuses()));
    GroupOperation groupByAccount =
        Aggregation.group(NodeExecutionKeys.accountId).count().as(ExecutionCountWithAccountResultKeys.count);
    ProjectionOperation projectAccount = Aggregation.project()
                                             .and(MONGODB_ID)
                                             .as(ExecutionCountWithAccountResultKeys.accountId)
                                             .andInclude(ExecutionCountWithAccountResultKeys.count);
    GroupOperation groupByModule =
        Aggregation.group(NodeExecutionKeys.module).count().as(ExecutionCountWithModuleResultKeys.count);
    ProjectionOperation projectModule = Aggregation.project()
                                            .and(MONGODB_ID)
                                            .as(ExecutionCountWithModuleResultKeys.module)
                                            .andInclude(ExecutionCountWithModuleResultKeys.count);
    GroupOperation groupByStepType =
        Aggregation.group(NodeExecutionKeys.type).count().as(ExecutionCountWithStepTypeResultKeys.count);
    ProjectionOperation projectStepType = Aggregation.project()
                                              .and(MONGODB_ID)
                                              .as(ExecutionCountWithStepTypeResultKeys.stepType)
                                              .andInclude(ExecutionCountWithStepTypeResultKeys.count);
    FacetOperation facetOperation = Aggregation.facet(groupByAccount, projectAccount)
                                        .as(ExecutionStatisticsKeys.accountStats)
                                        .and(groupByModule, projectModule)
                                        .as(ExecutionStatisticsKeys.moduleStats)
                                        .and(groupByStepType, projectStepType)
                                        .as(ExecutionStatisticsKeys.stepTypeStats);
    Aggregation aggregation = Aggregation.newAggregation(matchStage, facetOperation);
    List<ExecutionStatistics> executionStatisticsList =
        analyticsMongoTemplate.aggregate(aggregation, NodeExecution.class, ExecutionStatistics.class)
            .getMappedResults();
    return executionStatisticsList.size() > 0 ? executionStatisticsList.get(0) : null;
  }
}
