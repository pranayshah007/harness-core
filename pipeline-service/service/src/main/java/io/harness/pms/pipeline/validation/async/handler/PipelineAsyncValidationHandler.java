/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
@Slf4j
public class PipelineAsyncValidationHandler implements Runnable {
  private final PipelineValidationEvent validationEvent;
  private final PipelineAsyncValidationService validationService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;

  @Override
  public void run() {
    // When the thread is created, the status to begin with is INITIATED because after creation, it is possible that the
    // thread is picked up after a while rather than immediately. That's why the status is changed to IN_PROGRESS only
    // once the thread has been picked up
    validationService.updateEvent(
        validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());

    PipelineEntity pipelineEntity = validationEvent.getParams().getPipelineEntity();

    // Validate templates
    TemplateMergeResponseDTO templateMergeResponse;
    try {
      templateMergeResponse = pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, true);
      ValidationResult validationResult =
          ValidationResult.builder()
              .templateInputsResponse(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build())
              .build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, validationResult);
    } catch (NGTemplateResolveExceptionV2 e) {
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse = e.getValidateTemplateInputsResponseDTO();
      ValidationResult validationResult =
          ValidationResult.builder().templateInputsResponse(validateTemplateInputsResponse).build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, validationResult);
      return;
    }
    // Add Template Module Info temporarily to Pipeline Entity
    pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponse));
    // template merge response will be used for policy evaluation
  }
}