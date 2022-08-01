/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.observers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.OverlayInputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.repositories.inputset.PMSInputSetRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class InputSetPipelineObserver implements PipelineActionObserver {
  @Inject PMSInputSetRepository inputSetRepository;
  @Inject PMSInputSetService inputSetService;

  @Override
  public void onUpdate(PipelineUpdateEvent pipelineUpdateEvent) {
    PipelineEntity pipelineEntity = pipelineUpdateEvent.getNewPipeline();
    if (pipelineEntity.getStoreType() == StoreType.REMOTE) {
      return;
    }
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(pipelineEntity.getAccountId(),
        pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
        InputSetListTypePMS.INPUT_SET);

    List<InputSetEntity> allInputSets = inputSetRepository.findAll(criteria);
    allInputSets.forEach(inputSet -> checkIfInputSetIsValid(inputSet, pipelineEntity));

    Criteria criteriaOverlay = PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
        pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
        pipelineEntity.getIdentifier(), InputSetListTypePMS.OVERLAY_INPUT_SET);

    List<InputSetEntity> allOverlayInputSets = inputSetRepository.findAll(criteriaOverlay);
    allOverlayInputSets.forEach(inputSet -> checkIfOverlayInputSetIsValid(inputSet, pipelineEntity));
  }

  private void checkIfInputSetIsValid(InputSetEntity inputSet, PipelineEntity pipelineEntity) {
    InputSetErrorWrapperDTOPMS errorWrapperDTO =
        InputSetErrorsHelper.getErrorMap(pipelineEntity.getYaml(), inputSet.getYaml());
    if (errorWrapperDTO != null) {
      markAsInvalid(inputSet);
    } else {
      markAsValid(inputSet);
    }
  }

  private void checkIfOverlayInputSetIsValid(InputSetEntity overlayInputSet, PipelineEntity pipelineEntity) {
    try {
      OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, overlayInputSet);
    } catch (InvalidOverlayInputSetException e) {
      markAsInvalid(overlayInputSet);
      return;
    }
    markAsValid(overlayInputSet);
  }

  private void markAsInvalid(InputSetEntity inputSet) {
    String inputSetId = inputSet.getIdentifier();
    String pipelineId = inputSet.getPipelineIdentifier();
    boolean isMarked = inputSetService.switchValidationFlag(inputSet, true);
    if (!isMarked) {
      log.error("Could not mark input set " + inputSetId + " for pipeline " + pipelineId + " as invalid.");
    } else {
      log.info("Marked input set " + inputSetId + " for pipeline " + pipelineId + " as invalid.");
    }
  }

  private void markAsValid(InputSetEntity inputSet) {
    String inputSetId = inputSet.getIdentifier();
    String pipelineId = inputSet.getPipelineIdentifier();
    log.info("Input set " + inputSetId + " for pipeline " + pipelineId + " is valid.");
    if (inputSet.getIsInvalid()) {
      boolean isMarked = inputSetService.switchValidationFlag(inputSet, false);
      if (!isMarked) {
        log.error("Could not mark input set " + inputSetId + " for pipeline " + pipelineId + " as valid.");
      } else {
        log.info("Marked input set " + inputSetId + " for pipeline " + pipelineId + " as valid.");
      }
    }
  }

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    PipelineEntity pipelineEntity = pipelineDeleteEvent.getPipeline();
    inputSetService.deleteInputSetsOnPipelineDeletion(pipelineEntity);
    log.info("All inputSets of pipeline {} deleted", pipelineEntity.getIdentifier());
  }
}
