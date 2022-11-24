/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.repositories.pipeline.validation.async.PipelineValidationEventRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.model.ValidationAction;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineAsyncValidationServiceImpl implements PipelineAsyncValidationService {
  PipelineValidationEventRepository pipelineValidationEventRepository;

  @Override
  public PipelineValidationEvent startEvent(
      PipelineEntity entity, String branch, ValidationAction action, ValidationParams params) {
    return null;
  }

  @Override
  public PipelineValidationEvent updateEvent(String uuid, ValidationStatus status, ValidationResult result) {
    return null;
  }

  @Override
  public Optional<PipelineValidationEvent> getLatestEventByFQNAndAction(String fqn, ValidationAction action) {
    return pipelineValidationEventRepository.findByFqnAndAction(fqn, action);
  }

  @Override
  public Optional<PipelineValidationEvent> getEventByUuid(String uuid) {
    return pipelineValidationEventRepository.findById(uuid);
  }
}
