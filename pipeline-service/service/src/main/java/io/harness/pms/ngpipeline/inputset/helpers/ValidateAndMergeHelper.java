/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetsForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipelineForGivenStages;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.ngpipeline.inputset.beans.dto.InputSetMetadataDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.plan.execution.StagesExecutionHelper;
import io.harness.pms.stages.StagesExpressionExtractor;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PipelineGitXHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ValidateAndMergeHelper {
  private final PMSPipelineService pmsPipelineService;
  private final PMSInputSetService pmsInputSetService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final GitSyncSdkService gitSyncSdkService;

  public PipelineEntity getPipelineEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, boolean checkForStoreType,
      boolean loadFromCache) {
    // todo: move this to PMSPipelineService
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return getPipelineEntityForOldGitSyncFlow(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      return getPipelineEntity(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, checkForStoreType, loadFromCache);
    }
  }

  private PipelineEntity getPipelineEntityForOldGitSyncFlow(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String pipelineBranch, String pipelineRepoID) {
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();

    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
      Optional<PipelineEntity> pipelineEntity =
          pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
      if (pipelineEntity.isPresent()) {
        return pipelineEntity.get();
      } else {
        throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
            orgIdentifier, projectIdentifier, pipelineIdentifier));
      }
    }
  }

  private PipelineEntity getPipelineEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean checkForStoreType, boolean loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).build()).build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalPipelineEntity = pmsPipelineService.getPipeline(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, loadFromCache);
      }
    } else {
      long start = System.currentTimeMillis();
      optionalPipelineEntity = pmsPipelineService.getPipeline(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, loadFromCache);
      log.info(
          "[PMS_ValidateMerger] fetching and validating pipeline when update to new branch is false, took {}ms for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectIdentifier, orgIdentifier, accountId);
    }
    if (optionalPipelineEntity.isPresent()) {
      StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
      PipelineEntity pipelineEntity = optionalPipelineEntity.get();
      if (checkForStoreType && storeTypeInContext != null && pipelineEntity.getStoreType() != storeTypeInContext) {
        throw new InvalidRequestException("Input Set should have the same Store Type as the Pipeline it is for");
      }
      return pipelineEntity;
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  public InputSetTemplateResponseDTOPMS getInputSetTemplateResponseDTO(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> stageIdentifiers, boolean loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity = pmsPipelineService.getPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, loadFromCache);
    if (optionalPipelineEntity.isPresent()) {
      String template;
      List<String> replacedExpressions = null;

      String pipelineYaml = optionalPipelineEntity.get().getYaml();
      if (EmptyPredicate.isEmpty(stageIdentifiers)) {
        template = InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipeline(pipelineYaml);
      } else {
        String yaml = getYaml(accountId, orgIdentifier, projectIdentifier, pipelineYaml, optionalPipelineEntity);
        StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(yaml, stageIdentifiers);
        replacedExpressions = new ArrayList<>(StagesExpressionExtractor.getNonLocalExpressions(yaml, stageIdentifiers));
        template = createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages(
            yaml, pipelineYaml, stageIdentifiers);
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
          .resolveTemplateRefsInPipeline(accountId, orgIdentifier, projectIdentifier, pipelineYaml, BOOLEAN_FALSE_VALUE)
          .getMergedPipelineYaml();
    }
    return pipelineYaml;
  }

  //  use this method when the pipelineYaml is not available
  public String getPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> stageIdentifiers) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
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

  //  use this method when the pipelineYaml is not available
  public String getPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, List<String> stageIdentifiers) {
    String pipelineYaml = getPipelineEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, false)
                              .getYaml();
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return createTemplateFromPipeline(pipelineYaml);
    }
    return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
  }

  //  use this method when the pipelineYaml is available
  public String getPipelineTemplate(String pipelineYaml, List<String> stageIdentifiers) {
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return createTemplateFromPipeline(pipelineYaml);
    }
    return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
  }

  public JsonNode getMergeInputSetFromPipelineTemplateWithJsonNode(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch,
      String pipelineRepoID, List<String> stageIdentifiers) {
    return getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID, stageIdentifiers, null, false, false);
  }

  public String getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, List<String> inputSetReferences,
      String pipelineBranch, String pipelineRepoID, List<String> stageIdentifiers, String lastYamlToMerge,
      boolean loadFromCache) {
    JsonNode lastJsonNodeToMerge = null;
    if (isNotEmpty(lastYamlToMerge)) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(lastYamlToMerge);
    }
    return YamlUtils.writeYamlString(getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID,
        stageIdentifiers, lastJsonNodeToMerge, true, loadFromCache));
  }

  public JsonNode getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch,
      String pipelineRepoID, List<String> stageIdentifiers, JsonNode lastJsonNodeToMerge, boolean keepDefaultValues,
      boolean loadFromCache) {
    InputSetMetadataDTO inputSetMetadataDTO =
        getInputSetMetadataDTO(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetReferences,
            pipelineBranch, pipelineRepoID, stageIdentifiers, keepDefaultValues, loadFromCache);

    Set<String> inputSetVersions = inputSetMetadataDTO.getInputSetVersions();
    List<JsonNode> inputSetJsonNodeList = inputSetMetadataDTO.getInputSetJsonNodeList();
    JsonNode pipelineTemplate = inputSetMetadataDTO.getPipelineTemplate();
    if (inputSetVersions.contains(PipelineVersion.V0) && inputSetVersions.contains(PipelineVersion.V1)) {
      throw new InvalidRequestException("Input set versions 0 and 1 are not compatible");
    }
    if (inputSetVersions.contains(PipelineVersion.V1)) {
      return InputSetMergeHelper.mergeInputSetsV1(inputSetJsonNodeList);
    }

    if (!EmptyPredicate.isEmpty(lastJsonNodeToMerge)) {
      inputSetJsonNodeList.add(lastJsonNodeToMerge);
    }

    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSets(pipelineTemplate, inputSetJsonNodeList, false);
    }
    return mergeInputSetsForGivenStages(pipelineTemplate, inputSetJsonNodeList, false, stageIdentifiers);
  }

  public InputSetMetadataDTO getInputSetMetadataDTO(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, boolean keepDefaultValues, boolean loadFromCache) {
    GitSyncBranchContext branchContext = setupGitContext(accountId, orgIdentifier, projectIdentifier, pipelineBranch);
    PipelineEntity pipelineEntity;
    PipelineGitXHelper.setupGitParentEntityDetails(accountId, orgIdentifier, projectIdentifier, null, null);
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
      pipelineEntity = getPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineBranch, pipelineRepoID, false, loadFromCache);
    }
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    JsonNode pipelineTemplate = null;
    if (PipelineVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
      if (keepDefaultValues) {
        pipelineTemplate = EmptyPredicate.isEmpty(stageIdentifiers)
            ? createTemplateWithDefaultValuesFromPipeline(pipelineJsonNode)
            : createTemplateWithDefaultValuesFromPipelineForGivenStages(pipelineJsonNode, stageIdentifiers);
      } else {
        pipelineTemplate = EmptyPredicate.isEmpty(stageIdentifiers)
            ? createTemplateFromPipeline(pipelineJsonNode)
            : createTemplateFromPipelineForGivenStages(pipelineJsonNode, stageIdentifiers);
      }
      if (EmptyPredicate.isEmpty(pipelineTemplate) && EmptyPredicate.isNotEmpty(inputSetReferences)) {
        throw new InvalidRequestException(
            "Pipeline " + pipelineIdentifier + " does not have any runtime input. All existing input sets are invalid");
      }
    }
    List<JsonNode> inputSetJsonNodeList = new ArrayList<>();
    Set<String> inputSetVersions = new HashSet<>();

    if (inputSetReferences != null) {
      inputSetReferences.forEach(identifier -> {
        Optional<InputSetEntity> entity = pmsInputSetService.getWithoutValidations(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, false, false, loadFromCache);
        if (entity.isEmpty()) {
          return;
        }
        InputSetEntity inputSet = entity.get();
        inputSetVersions.add(inputSet.getHarnessVersion());
        checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(pipelineEntity, inputSet);
        if (inputSet.getInputSetEntityType() == InputSetEntityType.INPUT_SET) {
          inputSetJsonNodeList.add(YamlUtils.readAsJsonNode(inputSet.getYaml()));
        } else {
          List<String> overlayReferences = inputSet.getInputSetReferences();
          overlayReferences.forEach(id -> {
            Optional<InputSetEntity> entity2 = pmsInputSetService.getWithoutValidations(
                accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, id, false, false, loadFromCache);
            entity2.ifPresent(inputSetEntity -> {
              checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(pipelineEntity, entity2.get());
              inputSetJsonNodeList.add(YamlUtils.readAsJsonNode(inputSetEntity.getYaml()));
            });
          });
        }
      });
    }
    return InputSetMetadataDTO.builder()
        .inputSetVersions(inputSetVersions)
        .inputSetJsonNodeList(inputSetJsonNodeList)
        .pipelineTemplate(pipelineTemplate)
        .build();
  }

  public String mergeInputSetIntoPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String mergedRuntimeInputYaml, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, boolean loadFromCache) {
    String pipelineYaml = getPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineRepoID, false, loadFromCache)
                              .getYaml();
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineYaml, mergedRuntimeInputYaml, false);
    }
    return mergeInputSetIntoPipelineForGivenStages(pipelineYaml, mergedRuntimeInputYaml, false, stageIdentifiers);
  }

  @VisibleForTesting
  void checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(
      PipelineEntity pipelineEntity, InputSetEntity inputSetEntity) {
    if (pipelineEntity.getStoreType() == null || inputSetEntity.getStoreType() == null) {
      return;
    }
    if (!pipelineEntity.getStoreType().equals(inputSetEntity.getStoreType())) {
      throw NestedExceptionUtils.hintWithExplanationException("Please move the input-set from inline to remote.",
          "The pipeline is remote and input-set is inline",
          new InvalidRequestException(String.format(
              "Remote Pipeline %s cannot be used with inline input-set %s, please move input-set to from inline to remote to use them",
              pipelineEntity.getIdentifier(), inputSetEntity.getIdentifier())));
    }
  }

  private GitSyncBranchContext setupGitContext(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineBranch) {
    PipelineGitXHelper.setupGitParentEntityDetails(accountIdentifier, orgIdentifier, projectIdentifier, null, null);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return InputSetValidationHelper.buildGitSyncBranchContext(
        gitEntityInfo.getParentEntityRepoName(), pipelineBranch, gitEntityInfo.getParentEntityConnectorRef());
  }
}
