/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryReadHelper;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PmsExecutionSummaryRepositoryCustomImpl implements PmsExecutionSummaryRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final PmsExecutionSummaryReadHelper pmsExecutionSummaryReadHelper;

  @Override
  public PipelineExecutionSummaryEntity update(Query query, Update update) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed updating PipelineExecutionSummary; attempt: {}",
            "[Failed]: Failed updating PipelineExecutionSummary; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineExecutionSummaryEntity.class));
  }

  @Override
  public UpdateResult deleteAllExecutionsWhenPipelineDeleted(Query query, Update update) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed deleting PipelineExecutionSummary; attempt: {}",
            "[Failed]: Failed deleting PipelineExecutionSummary; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(() -> mongoTemplate.updateMulti(query, update, PipelineExecutionSummaryEntity.class));
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> findAll(Criteria criteria, Pageable pageable) {
    try {
      Query query = new Query(criteria).with(pageable);
      // Do not add directly the read helper inside the lambda, as secondary mongo reads were not going through if used
      // inside lambda in PageableExecutionUtils
      long count = pmsExecutionSummaryReadHelper.findCount(query);
      List<PipelineExecutionSummaryEntity> summaryEntities = pmsExecutionSummaryReadHelper.find(query);
      return PageableExecutionUtils.getPage(summaryEntities, pageable, () -> count);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Execution Status not found", ex);
    }
  }

  @Override
  public PipelineExecutionSummaryEntity findFirst(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, PipelineExecutionSummaryEntity.class);
  }

  private void queryFieldsForPipelineExecutionSummaryEntity(Query query) {
    query.fields().include(PlanExecutionSummaryKeys.uuid);
    query.fields().include(PlanExecutionSummaryKeys.runSequence);
    query.fields().include(PlanExecutionSummaryKeys.accountId);
    query.fields().include(PlanExecutionSummaryKeys.projectIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.orgIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.pipelineIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.name);
    query.fields().include(PlanExecutionSummaryKeys.retryExecutionMetadata);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.createdAt);
    query.fields().include(PlanExecutionSummaryKeys.lastUpdatedAt);
    query.fields().include(PlanExecutionSummaryKeys.version);
  }

  @Override
  public String fetchRootRetryExecutionId(String planExecutionId) {
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));

    queryFieldsForPipelineExecutionSummaryEntity(query);

    return mongoTemplate.findOne(query, PipelineExecutionSummaryEntity.class)
        .getRetryExecutionMetadata()
        .getRootExecutionId();
  }

  @Override
  public List<PipelineExecutionSummaryEntity> fetchPipelineSummaryEntityFromRootParentId(String rootParentId) {
    Query query = query(where(PlanExecutionSummaryKeys.rootExecutionId).is(rootParentId));

    queryFieldsForPipelineExecutionSummaryEntity(query);

    // RequiredFields
    query.fields().include(PlanExecutionSummaryKeys.startTs);
    query.fields().include(PlanExecutionSummaryKeys.endTs);
    query.fields().include(PlanExecutionSummaryKeys.status);

    query.with(by(Sort.Direction.DESC, PlanExecutionSummaryKeys.createdAt));
    return mongoTemplate.find(query, PipelineExecutionSummaryEntity.class);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
