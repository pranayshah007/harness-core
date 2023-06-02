/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.v2.ValidateTemplateReconcileResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.template.service.PipelineRefreshService;

import io.fabric8.utils.Pair;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineAsyncValidationHandler implements Runnable {
  private final PipelineValidationEvent validationEvent;
  private final boolean loadFromCache; // todo: see if this can be set to true always
  private final PipelineAsyncValidationService validationService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineGovernanceService pipelineGovernanceService;
  private final PipelineRefreshService pipelineRefreshService;

  public PipelineAsyncValidationHandler(PipelineValidationEvent validationEvent, boolean loadFromCache,
      PipelineAsyncValidationService validationService, PMSPipelineTemplateHelper pipelineTemplateHelper,
      PipelineGovernanceService pipelineGovernanceService, PipelineRefreshService pipelineRefreshService) {
    this.validationEvent = validationEvent;
    this.loadFromCache = loadFromCache;
    this.validationService = validationService;
    this.pipelineTemplateHelper = pipelineTemplateHelper;
    this.pipelineGovernanceService = pipelineGovernanceService;
    this.pipelineRefreshService = pipelineRefreshService;
  }

  @Override
  public void run() {
    // When the thread is created, the status to begin with is INITIATED because after creation, it is possible that the
    // thread is picked up after a while rather than immediately. That's why the status is changed to IN_PROGRESS only
    // once the thread has been picked up
    validationService.updateEvent(
        validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    PipelineEntity pipelineEntity = validationEvent.getParams().getPipelineEntity();

    // Reconcile check
    ValidateTemplateReconcileResponseDTO validateTemplateReconcileResponseDTO = checkForReconcile(pipelineEntity);

    /* If reconcile is needed then we are setting the Yaml validation and Governance Policy check as default
        and setting the status success
     */
    if (validateTemplateReconcileResponseDTO.isReconcileNeeded()) {
      ValidationResult validationResult =
          ValidationResult.builder()
              .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
              .build();
      validationResult =
          validationResult.withValidateTemplateReconcileResponseDTO(validateTemplateReconcileResponseDTO);
      validationResult =
          validationResult.withGovernanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build());
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.SUCCESS, validationResult);
      return;
    }

    // Validate templates
    Pair<ValidationResult, TemplateMergeResponseDTO> templateValidation =
        validateTemplatesAndUpdateResult(pipelineEntity);
    ValidationResult templateValidationResult =
        templateValidation.getFirst().withValidateTemplateReconcileResponseDTO(validateTemplateReconcileResponseDTO);
    TemplateMergeResponseDTO templateMergeResponse = templateValidation.getSecond();
    if (!templateValidationResult.getTemplateValidationResponse().isValidYaml()) {
      return;
    }
    // Evaluate Policies
    evaluatePoliciesAndUpdateResult(pipelineEntity, templateMergeResponse, templateValidationResult);
  }

  Pair<ValidationResult, TemplateMergeResponseDTO> validateTemplatesAndUpdateResult(PipelineEntity pipelineEntity) {
    ValidationResult templateValidationResult;

    TemplateMergeResponseDTO templateMergeResponse = null;
    try {
      templateMergeResponse = pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, true, loadFromCache);
    } catch (Exception ex) {
      log.error(String.format("Error occurred while resolving template!"), ex);

      templateValidationResult =
          ValidationResult.builder()
              .templateValidationResponse(
                  TemplateValidationResponseDTO.builder().validYaml(false).exceptionMessage(ex.getMessage()).build())
              .build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, templateValidationResult);
      return new Pair<>(templateValidationResult, templateMergeResponse);
    }

    templateValidationResult =
        ValidationResult.builder()
            .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
            .build();
    validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, templateValidationResult);
    // Add Template Module Info temporarily to Pipeline Entity
    pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponse));
    return new Pair<>(templateValidationResult, templateMergeResponse);
  }

  void evaluatePoliciesAndUpdateResult(PipelineEntity pipelineEntity, TemplateMergeResponseDTO templateMergeResponse,
      ValidationResult templateValidationResult) {
    // policy evaluation will be done on the pipeline yaml which has both the template refs and the resolved template
    String mergedPipelineYamlWithTemplateRefs = templateMergeResponse.getMergedPipelineYamlWithTemplateRef();
    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRules(
        pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
        mergedPipelineYamlWithTemplateRefs);
    ValidationResult governanceValidationResult = templateValidationResult.withGovernanceMetadata(governanceMetadata);
    if (governanceMetadata.getDeny()) {
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, governanceValidationResult);
    } else {
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.SUCCESS, governanceValidationResult);
    }
  }

  public ValidateTemplateReconcileResponseDTO checkForReconcile(PipelineEntity pipelineEntity) {
    try {
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
          pipelineRefreshService.validateTemplateInputsInPipeline(pipelineEntity.getAccountId(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
              "false");

      return ValidateTemplateReconcileResponseDTO.builder()
          .isReconcileNeeded(!validateTemplateInputsResponseDTO.isValidYaml())
          .build();
    } catch (Exception ex) {
      log.error("Error occurred while checking reconcile!", ex);
    }
    return ValidateTemplateReconcileResponseDTO.builder().isReconcileNeeded(false).build();
  }
}