/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.mongo.helper.AnalyticsMongoTemplateHolder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
@Singleton
public class PlanExecutionRepositoryCustomImpl implements PlanExecutionRepositoryCustom {
  private static final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate analyticsMongoTemplate;

  @Inject
  public PlanExecutionRepositoryCustomImpl(
      MongoTemplate mongoTemplate, AnalyticsMongoTemplateHolder analyticsMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.analyticsMongoTemplate = analyticsMongoTemplateHolder.getAnalyticsMongoTemplate();
  }

  @Override
  public PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    query.fields().exclude(PlanExecutionKeys.uuid);
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution updatePlanExecution(Query query, Update updateOps, boolean upsert) {
    return mongoTemplate.findAndModify(
        query, updateOps, new FindAndModifyOptions().upsert(upsert).returnNew(true), PlanExecution.class);
  }

  @Override
  public void multiUpdatePlanExecution(Query query, Update updateOps) {
    mongoTemplate.updateMulti(query, updateOps, PlanExecution.class);
  }
  @Override
  public PlanExecution getPlanExecutionWithProjections(String planExecutionId, List<String> excludedFieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : excludedFieldNames) {
      query.fields().exclude(fieldName);
    }
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getPlanExecutionWithIncludedProjections(
      String planExecutionId, List<String> includedFieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : includedFieldNames) {
      query.fields().include(fieldName);
    }
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  public CloseableIterator<PlanExecution> fetchPlanExecutionsFromAnalytics(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validatePlanExecutionStreamQuery(query);
    return analyticsMongoTemplate.stream(query, PlanExecution.class);
  }

  private void validatePlanExecutionStreamQuery(Query query) {
    if (query.getMeta().getCursorBatchSize() == null || query.getMeta().getCursorBatchSize() <= 0
        || query.getMeta().getCursorBatchSize() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "PlanExecution query should have cursorBatch limit within max batch size- " + MAX_BATCH_SIZE);
    }
    validatePlanExecutionProjection(query);
  }

  private void validatePlanExecutionProjection(Query query) {
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("PlanExecution list query should have projection fields");
    }
  }
}
