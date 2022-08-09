/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSets;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetsForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.plan.execution.StagesExecutionHelper;
import io.harness.pms.stages.StagesExpressionExtractor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ValidateAndMergeHelper {
  private final PMSPipelineService pmsPipelineService;
  private final PMSInputSetService pmsInputSetService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final GitSyncSdkService gitSyncSdkService;

  public String getPipelineYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, boolean checkForStoreType) {
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return getPipelineYamlForOldGitSyncFlow(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      return getPipelineYaml(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, checkForStoreType);
    }
  }

  private String getPipelineYamlForOldGitSyncFlow(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID) {
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();

    String pipelineYaml;
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
      Optional<PipelineEntity> pipelineEntity =
          pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
      if (pipelineEntity.isPresent()) {
        pipelineYaml = pipelineEntity.get().getYaml();
      } else {
        throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
            orgIdentifier, projectIdentifier, pipelineIdentifier));
      }
    }
    return pipelineYaml;
  }

  private String getPipelineYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean checkForStoreType) {
    Optional<PipelineEntity> optionalPipelineEntity;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).build()).build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalPipelineEntity =
            pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
      }
    } else {
      optionalPipelineEntity =
          pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    }
    if (optionalPipelineEntity.isPresent()) {
      StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
      PipelineEntity pipelineEntity = optionalPipelineEntity.get();
      if (checkForStoreType && storeTypeInContext != null && pipelineEntity.getStoreType() != storeTypeInContext) {
        throw new InvalidRequestException("Input Set should have the same Store Type as the Pipeline it is for");
      }
      return pipelineEntity.getYaml();
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  public InputSetTemplateResponseDTOPMS getInputSetTemplateResponseDTO(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> stageIdentifiers) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    if (optionalPipelineEntity.isPresent()) {
      String template;
      List<String> replacedExpressions = null;

      String pipelineYaml = optionalPipelineEntity.get().getYaml();
      if (EmptyPredicate.isEmpty(stageIdentifiers)) {
        template = createTemplateFromPipeline(pipelineYaml);
      } else {
        String yaml = getYaml(accountId, orgIdentifier, projectIdentifier, pipelineYaml, optionalPipelineEntity);
        StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(yaml, stageIdentifiers);
        replacedExpressions = new ArrayList<>(StagesExpressionExtractor.getNonLocalExpressions(yaml, stageIdentifiers));
        template = createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
      }

      boolean hasInputSets = pmsInputSetService.checkForInputSetsForPipeline(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

      return InputSetTemplateResponseDTOPMS.builder()
          .inputSetTemplateYaml(template)
          .replacedExpressions(replacedExpressions)
          .modules(optionalPipelineEntity.get().getFilters().keySet())
          .hasInputSets(hasInputSets)
          .build();
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  private String getYaml(String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml,
      Optional<PipelineEntity> optionalPipelineEntity) {
    if (optionalPipelineEntity.isPresent()
        && Boolean.TRUE.equals(optionalPipelineEntity.get().getTemplateReference())) {
      // returning resolved yaml
      return pipelineTemplateHelper
          .resolveTemplateRefsInPipeline(accountId, orgIdentifier, projectIdentifier, pipelineYaml)
          .getMergedPipelineYaml();
    }
    return pipelineYaml;
  }

  public String getPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> stageIdentifiers) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    if (optionalPipelineEntity.isPresent()) {
      String pipelineYaml = optionalPipelineEntity.get().getYaml();
      if (EmptyPredicate.isEmpty(stageIdentifiers)) {
        return createTemplateFromPipeline(pipelineYaml);
      } else {
        return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
      }

    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  public String getPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, List<String> stageIdentifiers) {
    String pipelineYaml = getPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID, false);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return createTemplateFromPipeline(pipelineYaml);
    }
    return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
  }

  public String getMergeInputSetFromPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers) {
    String pipelineTemplate = getPipelineTemplate(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineRepoID, stageIdentifiers);
    if (EmptyPredicate.isEmpty(pipelineTemplate)) {
      throw new InvalidRequestException(
          "Pipeline " + pipelineIdentifier + " does not have any runtime input. All existing input sets are invalid");
    }

    Set<String> invalidReferences = new HashSet<>();
    List<String> inputSetYamlList = new ArrayList<>();
    inputSetReferences.forEach(identifier -> {
      Optional<InputSetEntity> entity = pmsInputSetService.getWithoutValidations(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, false);
      if (!entity.isPresent()) {
        invalidReferences.add(identifier);
        return;
      }
      InputSetEntity inputSet = entity.get();
      if (inputSet.getIsInvalid()) {
        invalidReferences.add(identifier);
        return;
      }
      if (inputSet.getInputSetEntityType() == InputSetEntityType.INPUT_SET) {
        inputSetYamlList.add(inputSet.getYaml());
        if (InputSetErrorsHelper.getErrorMap(pipelineTemplate, inputSet.getYaml()) != null) {
          invalidReferences.add(identifier);
        }
      } else {
        List<String> overlayReferences = inputSet.getInputSetReferences();
        overlayReferences.forEach(id -> {
          Optional<InputSetEntity> entity2 = pmsInputSetService.getWithoutValidations(
              accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, id, false);
          if (!entity2.isPresent()) {
            invalidReferences.add(identifier);
          } else {
            inputSetYamlList.add(entity2.get().getYaml());
            if (InputSetErrorsHelper.getErrorMap(pipelineTemplate, entity2.get().getYaml()) != null) {
              invalidReferences.add(identifier);
            }
          }
        });
      }
    });

    if (EmptyPredicate.isNotEmpty(invalidReferences)) {
      throw new InvalidInputSetException("Some of the references provided are invalid",
          InputSetErrorWrapperDTOPMS.builder().invalidInputSetReferences(new ArrayList<>(invalidReferences)).build());
    }

    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return mergeInputSets(pipelineTemplate, inputSetYamlList, false);
    }
    return mergeInputSetsForGivenStages(pipelineTemplate, inputSetYamlList, false, stageIdentifiers);
  }

  public String mergeInputSetIntoPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String mergedRuntimeInputYaml, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers) {
    String pipelineYaml = getPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID, false);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineYaml, mergedRuntimeInputYaml, false);
    }
    return mergeInputSetIntoPipelineForGivenStages(pipelineYaml, mergedRuntimeInputYaml, false, stageIdentifiers);
  }
}
