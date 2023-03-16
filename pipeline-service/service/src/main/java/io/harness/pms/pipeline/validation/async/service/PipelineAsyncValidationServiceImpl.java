/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.handler.PipelineAsyncValidationHandler;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.repositories.pipeline.validation.async.PipelineValidationEventRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineAsyncValidationServiceImpl implements PipelineAsyncValidationService {
  private final PipelineValidationEventRepository pipelineValidationEventRepository;
  private final Executor executor;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineGovernanceService pipelineGovernanceService;

  @Inject
  public PipelineAsyncValidationServiceImpl(PipelineValidationEventRepository pipelineValidationEventRepository,
      @Named("PipelineAsyncValidationExecutorService") Executor executor,
      PMSPipelineTemplateHelper pipelineTemplateHelper, PipelineGovernanceService pipelineGovernanceService) {
    this.pipelineValidationEventRepository = pipelineValidationEventRepository;
    this.executor = executor;
    this.pipelineTemplateHelper = pipelineTemplateHelper;
    this.pipelineGovernanceService = pipelineGovernanceService;
  }

  @Override
  public PipelineValidationEvent startEvent(
      PipelineEntity entity, String branch, Action action, boolean loadFromCache) {
    String fqn = PipelineAsyncValidationHelper.buildFQN(entity, branch);
    PipelineValidationEvent pipelineValidationEvent =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.INITIATED)
            .fqn(fqn)
            .action(action)
            .params(ValidationParams.builder().pipelineEntity(entity).build())
            .result(ValidationResult.builder().build())
            .startTs(System.currentTimeMillis())
            .build();
    PipelineValidationEvent savedPipelineValidationEvent =
        pipelineValidationEventRepository.save(pipelineValidationEvent);

    executor.execute(new PipelineAsyncValidationHandler(
        savedPipelineValidationEvent, loadFromCache, this, pipelineTemplateHelper, pipelineGovernanceService));
    return savedPipelineValidationEvent;
  }

  @Override
  public PipelineValidationEvent createRecordForSuccessfulSyncValidation(
      PipelineEntity pipelineEntity, String branch, GovernanceMetadata governanceMetadata, Action action) {
    String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, branch);
    PipelineValidationEvent pipelineValidationEvent =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.SUCCESS)
            .fqn(fqn)
            .action(action)
            .params(ValidationParams.builder().pipelineEntity(pipelineEntity).build())
            .result(ValidationResult.builder().governanceMetadata(governanceMetadata).build())
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .build();
    return pipelineValidationEventRepository.save(pipelineValidationEvent);
  }

  @Override
  public PipelineValidationEvent updateEvent(String uuid, ValidationStatus status, ValidationResult result) {
    Criteria criteria = PipelineAsyncValidationHelper.getCriteriaForUpdate(uuid);
    Update updateOperations = PipelineAsyncValidationHelper.getUpdateOperations(status, result);
    return pipelineValidationEventRepository.update(criteria, updateOperations);
  }

  @Override
  public Optional<PipelineValidationEvent> getLatestEventByFQNAndAction(String fqn, Action action) {
    return pipelineValidationEventRepository.findLatestValidEvent(fqn, action);
  }

  @Override
  public Optional<PipelineValidationEvent> getEventByUuid(String uuid) {
    return pipelineValidationEventRepository.findById(uuid);
  }
}
