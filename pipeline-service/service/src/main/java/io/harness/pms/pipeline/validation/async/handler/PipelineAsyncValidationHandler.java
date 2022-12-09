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
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.spec.server.commons.model.GovernanceMetadata;

import io.fabric8.utils.Pair;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
@Slf4j
public class PipelineAsyncValidationHandler implements Runnable {
  private final PipelineValidationEvent validationEvent;
  private final PipelineAsyncValidationService validationService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineGovernanceService pipelineGovernanceService;

  @Override
  public void run() {
    // When the thread is created, the status to begin with is INITIATED because after creation, it is possible that the
    // thread is picked up after a while rather than immediately. That's why the status is changed to IN_PROGRESS only
    // once the thread has been picked up
    validationService.updateEvent(
        validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    PipelineEntity pipelineEntity = validationEvent.getParams().getPipelineEntity();

    // Validate templates
    Pair<ValidationResult, TemplateMergeResponseDTO> templateValidation =
        validateTemplatesAndUpdateResult(pipelineEntity);
    ValidationResult templateValidationResult = templateValidation.getFirst();
    TemplateMergeResponseDTO templateMergeResponse = templateValidation.getSecond();
    if (!templateValidationResult.getTemplateInputsResponse().isValidYaml()) {
      return;
    }

    // Evaluate Policies
    ValidationResult governanceValidationResult =
        evaluatePoliciesAndUpdateResult(pipelineEntity, templateMergeResponse, templateValidationResult);
    if (governanceValidationResult.getGovernanceResponse().isDeny()) {
      return;
    }

    // todo: filter creation
  }

  Pair<ValidationResult, TemplateMergeResponseDTO> validateTemplatesAndUpdateResult(PipelineEntity pipelineEntity) {
    ValidationResult templateValidationResult;
    try {
      TemplateMergeResponseDTO templateMergeResponse =
          pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, true);
      templateValidationResult =
          ValidationResult.builder()
              .templateInputsResponse(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build())
              .build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, templateValidationResult);
      // Add Template Module Info temporarily to Pipeline Entity
      pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponse));
      return new Pair<>(templateValidationResult, templateMergeResponse);
    } catch (NGTemplateResolveExceptionV2 e) {
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse = e.getValidateTemplateInputsResponseDTO();
      templateValidationResult =
          ValidationResult.builder().templateInputsResponse(validateTemplateInputsResponse).build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, templateValidationResult);
      return new Pair<>(templateValidationResult, null);
    }
  }

  ValidationResult evaluatePoliciesAndUpdateResult(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponse, ValidationResult templateValidationResult) {
    // policy evaluation will be done on the pipeline yaml which has both the template refs and the resolved template
    String mergedPipelineYamlWithTemplateRefs = templateMergeResponse.getMergedPipelineYamlWithTemplateRef();
    io.harness.governance.GovernanceMetadata protoMetadata = pipelineGovernanceService.validateGovernanceRules(
        pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
        mergedPipelineYamlWithTemplateRefs);
    GovernanceMetadata governanceMetadata = PipelinesApiUtils.buildGovernanceMetadataFromProto(protoMetadata);
    ValidationResult governanceValidationResult = templateValidationResult.withGovernanceResponse(governanceMetadata);
    if (protoMetadata.getDeny()) {
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, governanceValidationResult);
    }
    validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, governanceValidationResult);
    return governanceValidationResult;
  }
}