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
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;

import java.util.Optional;

@OwnedBy(PIPELINE)
public interface PipelineAsyncValidationService {
  PipelineValidationEvent startEvent(PipelineEntity entity, String branch, Action action, boolean loadFromCache);

  PipelineValidationEvent createRecordForSuccessfulSyncValidation(
      PipelineEntity pipelineEntity, String branch, GovernanceMetadata governanceMetadata, Action action);

  PipelineValidationEvent updateEvent(String uuid, ValidationStatus status, ValidationResult result);

  Optional<PipelineValidationEvent> getLatestEventByFQNAndAction(String fqn, Action action);

  Optional<PipelineValidationEvent> getEventByUuid(String uuid);
}
