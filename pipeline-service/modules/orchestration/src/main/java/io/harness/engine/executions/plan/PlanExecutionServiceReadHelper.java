package io.harness.engine.executions.plan;
/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PlanExecutionServiceReadHelper {
  @Inject @Named("secondary-mongo") public MongoTemplate secondaryMongoTemplate;

  public PlanExecution findNextExecutionToRun(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Criteria criteria =
        new Criteria()
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
            .is(accountId)
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.orgIdentifier)
            .is(orgId)
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.projectIdentifier)
            .is(projectId)
            .and(PlanExecution.PlanExecutionKeys.metadata + ".pipelineIdentifier")
            .is(pipelineIdentifier)
            .and(PlanExecution.PlanExecutionKeys.status)
            .is(Status.QUEUED);
    return secondaryMongoTemplate.findOne(
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, PlanExecution.PlanExecutionKeys.createdAt)),
        PlanExecution.class);
  }

  public long findRunningExecutionsForGivenPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Criteria criteria =
        new Criteria()
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
            .is(accountId)
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.orgIdentifier)
            .is(orgId)
            .and(PlanExecution.PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.projectIdentifier)
            .is(projectId)
            .and(PlanExecution.PlanExecutionKeys.metadata + ".pipelineIdentifier")
            .is(pipelineIdentifier)
            .and(PlanExecution.PlanExecutionKeys.status)
            .in(StatusUtils.activeStatuses());
    return secondaryMongoTemplate.count(new Query(criteria), PlanExecution.class);
  }
}
