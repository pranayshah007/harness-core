/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.RetryStagesMetadata;
import io.harness.repositories.PlanExecutionMetadataRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanExecutionMetadataServiceImpl implements PlanExecutionMetadataService {
  private final PlanExecutionMetadataRepository planExecutionMetadataRepository;

  @Inject
  public PlanExecutionMetadataServiceImpl(PlanExecutionMetadataRepository planExecutionMetadataRepository) {
    this.planExecutionMetadataRepository = planExecutionMetadataRepository;
  }

  @Override
  public Optional<PlanExecutionMetadata> findByPlanExecutionId(String planExecutionId) {
    return planExecutionMetadataRepository.findByPlanExecutionId(planExecutionId);
  }

  @Override
  public PlanExecutionMetadata save(PlanExecutionMetadata planExecutionMetadata) {
    return planExecutionMetadataRepository.save(planExecutionMetadata);
  }

  @Override
  public void deleteMetadataForGivenPlanExecutionIds(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      planExecutionMetadataRepository.deleteAllByPlanExecutionIdIn(planExecutionIds);
      return true;
    });
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }

    Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Update update = new Update();
    update.set(PlanExecutionMetadataKeys.validUntil, ttlDate);
    planExecutionMetadataRepository.multiUpdatePlanExecution(criteria, update);
  }

  public String getNotesForExecution(String planExecutionId) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataRepository.getWithFieldsIncludedFromSecondary(
        planExecutionId, Set.of(PlanExecutionMetadataKeys.notes));
    return getNotesOrEmptyString(planExecutionMetadata);
  }

  public RetryStagesMetadata getRetryStagesMetadata(String planExecutionId) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataRepository.getWithFieldsIncludedFromSecondary(
        planExecutionId, Set.of(PlanExecutionMetadataKeys.retryStagesMetadata));
    return planExecutionMetadata.getRetryStagesMetadata();
  }

  public String updateNotesForExecution(String planExecutionId, String notes) {
    Criteria criteria = where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Update update = new Update();
    update.set(PlanExecutionMetadataKeys.notes, notes);

    Optional<PlanExecutionMetadata> planExecutionMetadata =
        Optional.ofNullable(planExecutionMetadataRepository.updatePlanExecution(criteria, update));
    if (!planExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(format("Execution with id [%s] is not present or deleted", planExecutionId));
    }

    return getNotesOrEmptyString(planExecutionMetadata.get());
  }

  private String getNotesOrEmptyString(PlanExecutionMetadata planExecutionMetadata) {
    if (EmptyPredicate.isEmpty(planExecutionMetadata.getNotes())) {
      return "";
    }
    return planExecutionMetadata.getNotes();
  }

  @Override
  public PlanExecutionMetadata getWithFieldsIncludedFromSecondary(String planExecutionId, Set<String> fieldsToInclude) {
    return planExecutionMetadataRepository.getWithFieldsIncludedFromSecondary(planExecutionId, fieldsToInclude);
  }
}
