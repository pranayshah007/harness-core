/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.utils.PipelineExceptionsHelper.ERROR_PIPELINE_BRANCH_NOT_PROVIDED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.yaml.PipelineVersion;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetValidationHelper {
  public void checkForPipelineStoreType(PipelineEntity pipelineEntity) {
    StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
    boolean isForRemote = storeTypeInContext != null && storeTypeInContext.equals(StoreType.REMOTE);
    boolean isPipelineRemote = pipelineEntity.getStoreType() == StoreType.REMOTE;
    if (isForRemote ^ isPipelineRemote) {
      throw new InvalidRequestException("Input Set should have the same Store Type as the Pipeline it is for");
    }
  }

  // this method is not for old git sync
  public void validateInputSet(
      PMSInputSetService inputSetService, InputSetEntity inputSetEntity, boolean hasNewYamlStructure) {
    switch (inputSetEntity.getHarnessVersion()) {
      case PipelineVersion.V1:
        return;
      case PipelineVersion.V0:
        break;
      default:
        throw new IllegalStateException("version not supported");
    }
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();
    InputSetEntityType type = inputSetEntity.getInputSetEntityType();
    if (type.equals(InputSetEntityType.INPUT_SET)) {
      if (!hasNewYamlStructure) {
        validateIdentifyingFieldsInYAML(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
      }
    } else {
      OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity);
    }
  }

  /*
  If the Input Set create/update is to a new branch, in that case, the pipeline needs to be fetched from the base branch
  (the branch from which the new branch will be checked out). This method facilitates that by first whether the
  operation is to a new branch or not. If it is to a new branch, then it creates a guard to fetch the pipeline from the
  base branch. If not, no guard is needed.
  */
  public PipelineEntity getPipelineEntity(PMSPipelineService pmsPipelineService, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> optionalPipelineEntity;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).build()).build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalPipelineEntity = pmsPipelineService.getPipeline(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
      }
    } else {
      optionalPipelineEntity =
          pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  public String getPipelineYamlForOldGitSyncFlow(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String pipelineBranch,
      String pipelineRepoID) {
    if (EmptyPredicate.isEmpty(pipelineBranch) || EmptyPredicate.isEmpty(pipelineRepoID)) {
      return getPipelineYamlForOldGitSyncFlowInternal(
          pmsPipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();

    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
      return getPipelineYamlForOldGitSyncFlowInternal(
          pmsPipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
  }

  String getPipelineYamlForOldGitSyncFlowInternal(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (pipelineEntity.isPresent()) {
      return pipelineEntity.get().getYaml();
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  void validateIdentifyingFieldsInYAML(
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String yaml) {
    String identifier = InputSetYamlHelper.getStringField(yaml, "identifier", "inputSet");
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("Identifier cannot be empty");
    }
    if (identifier.length() > 127) {
      throw new InvalidRequestException("Input Set identifier length cannot be more that 127 characters.");
    }
    InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml, pipelineIdentifier);
    InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml, "inputSet", orgIdentifier, projectIdentifier);
  }

  public InputSetYamlDiffDTO getYAMLDiff(GitSyncSdkService gitSyncSdkService, PMSInputSetService inputSetService,
      PMSPipelineService pipelineService, ValidateAndMergeHelper validateAndMergeHelper, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String inputSetIdentifier,
      String pipelineBranch, String pipelineRepoID, boolean allowDifferentReposForPipelineAndInputSets) {
    //    get input set and pipeline metadata for checking the if same repos or different repos to set the branch for
    //    input set
    InputSetEntity inputSetMetadata = inputSetService.getMetadata(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, false, false, true);

    PipelineEntity pipelineMetadata = pipelineService.getPipelineMetadata(inputSetMetadata.getAccountIdentifier(),
        inputSetMetadata.getOrgIdentifier(), inputSetMetadata.getProjectIdentifier(),
        inputSetMetadata.getPipelineIdentifier(), false, true);
    // fetch complete input set yaml
    InputSetEntity inputSetEntity = getInputSetEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineMetadata, inputSetMetadata, inputSetIdentifier, inputSetService,
        allowDifferentReposForPipelineAndInputSets);

    EntityGitDetails entityGitDetails = PMSInputSetElementMapper.getEntityGitDetails(inputSetEntity);
    // fetch complete pipeline yaml
    String pipelineYaml = getPipelineYaml(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineMetadata, pipelineRepoID, pipelineService, gitSyncSdkService);

    InputSetYamlDiffDTO yamlDiffDTO;
    if (inputSetEntity.getInputSetEntityType() == InputSetEntityType.INPUT_SET) {
      yamlDiffDTO = getYAMLDiffForInputSet(validateAndMergeHelper, inputSetEntity, pipelineYaml);
    } else {
      yamlDiffDTO = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
          gitSyncSdkService, inputSetService, inputSetEntity, pipelineYaml);
    }

    yamlDiffDTO.setGitDetails(entityGitDetails);
    return yamlDiffDTO;
  }

  private InputSetEntity getInputSetEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata,
      InputSetEntity inputSetMetadata, String inputSetIdentifier, PMSInputSetService inputSetService,
      boolean allowDifferentReposForPipelineAndInputSets) {
    Optional<InputSetEntity> optionalInputSetEntity;
    if (allowDifferentReposForPipelineAndInputSets && EmptyPredicate.isNotEmpty(pipelineMetadata.getRepo())
        && EmptyPredicate.isNotEmpty(inputSetMetadata.getRepo())
        && pipelineMetadata.getRepo().equals(inputSetMetadata.getRepo())) {
      if (EmptyPredicate.isNotEmpty(pipelineBranch)) {
        GitSyncBranchContext branchContext =
            buildGitSyncBranchContext(inputSetMetadata.getRepo(), pipelineBranch, inputSetMetadata.getConnectorRef());
        //      Fetch input set when pipeline and input set are in same repos
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
          optionalInputSetEntity = inputSetService.getWithoutValidations(
              accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, false, false, false);
        }
      } else {
        throw new InvalidRequestException(ERROR_PIPELINE_BRANCH_NOT_PROVIDED);
      }
    } else {
      //      Fetch input set when pipeline and input set are in different repos
      optionalInputSetEntity = inputSetService.getWithoutValidations(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, false, false, false);
    }
    if (optionalInputSetEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    return optionalInputSetEntity.get();
  }

  private String getPipelineYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata, String pipelineRepoID,
      PMSPipelineService pipelineService, GitSyncSdkService gitSyncSdkService) {
    boolean isOldGitSyncFlow = gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
    String pipelineYaml;
    if (isOldGitSyncFlow) {
      //      Old git experience flow for fetching the pipeline
      pipelineYaml = getPipelineYamlForOldGitSyncFlow(pipelineService, accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      //      New git experience flow for fetching the pipeline
      pipelineYaml = getPipelineYamlForGitX(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineBranch, pipelineMetadata, pipelineService);
    }
    return pipelineYaml;
  }

  private String getPipelineYamlForGitX(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata,
      PMSPipelineService pipelineService) {
    PipelineEntity pipelineEntity;
    if (EmptyPredicate.isNotEmpty(pipelineBranch)) {
      GitSyncBranchContext branchContext =
          buildGitSyncBranchContext(pipelineMetadata.getRepo(), pipelineBranch, pipelineMetadata.getConnectorRef());

      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        pipelineEntity =
            getPipelineEntity(pipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      }
    } else {
      pipelineEntity =
          getPipelineEntity(pipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    return pipelineEntity.getYaml();
  }

  InputSetYamlDiffDTO getYAMLDiffForInputSet(
      ValidateAndMergeHelper validateAndMergeHelper, InputSetEntity inputSetEntity, String pipelineYaml) {
    String inputSetYaml = inputSetEntity.getYaml();
    String newInputSetYaml = InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML(pipelineYaml, inputSetYaml);
    if (EmptyPredicate.isEmpty(newInputSetYaml)) {
      String pipelineTemplate = validateAndMergeHelper.getPipelineTemplate(pipelineYaml, null);
      if (EmptyPredicate.isEmpty(pipelineTemplate)) {
        return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(true).build();
      } else {
        return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(false).build();
      }
    }
    return InputSetYamlDiffDTO.builder()
        .oldYAML(inputSetYaml)
        .newYAML(newInputSetYaml)
        .isInputSetEmpty(false)
        .noUpdatePossible(false)
        .build();
  }

  public GitSyncBranchContext buildGitSyncBranchContext(String repo, String branch, String connectorRef) {
    return GitSyncBranchContext.builder()
        .gitBranchInfo(GitEntityInfo.builder().repoName(repo).branch(branch).connectorRef(connectorRef).build())
        .build();
  }
}
