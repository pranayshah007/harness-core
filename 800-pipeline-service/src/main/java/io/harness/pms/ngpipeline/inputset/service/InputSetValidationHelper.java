package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetValidationHelper {
  // this method is not for old git sync
  public void validateInputSet(PMSInputSetService inputSetService, PMSPipelineService pipelineService,
      InputSetEntity inputSetEntity, boolean checkForStoreType) {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();
    InputSetEntityType type = inputSetEntity.getInputSetEntityType();

    PipelineEntity pipelineEntity = getPipelineEntityAndCheckForStoreType(
        pipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, checkForStoreType);
    if (type.equals(InputSetEntityType.INPUT_SET)) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO =
          validateInputSet(pipelineEntity, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
      if (errorWrapperDTO != null) {
        throw new InvalidInputSetException(
            "Some fields in the Input Set are invalid.", errorWrapperDTO, inputSetEntity);
      }
    } else {
      OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity);
    }
  }

  InputSetErrorWrapperDTOPMS validateInputSet(PipelineEntity pipelineEntity, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String yaml) {
    validateIdentifyingFieldsInYAML(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    String pipelineYAML = pipelineEntity.getYaml();
    return InputSetErrorsHelper.getErrorMap(pipelineYAML, yaml);
  }

  /*
  We only need to check for store type in input set create flow. During update, it can be assumed that the store type
  for pipeline and input set are in sync
   */
  PipelineEntity getPipelineEntityAndCheckForStoreType(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean checkForStoreType) {
    PipelineEntity pipelineEntity =
        getPipelineEntity(pmsPipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (checkForStoreType) {
      StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
      boolean isForRemote = storeTypeInContext != null && storeTypeInContext.equals(StoreType.REMOTE);
      boolean isPipelineRemote = pipelineEntity.getStoreType() == StoreType.REMOTE;
      if (isForRemote ^ isPipelineRemote) {
        throw new InvalidRequestException("Input Set should have the same Store Type as the Pipeline it is for");
      }
    }

    return pipelineEntity;
  }

  /*
  If the Input Set create/update is to a new branch, in that case, the pipeline needs to be fetched from the base branch
  (the branch from which the new branch will be checked out). This method facilitates that by first whether the
  operation is to a new branch or not. If it is to a new branch, then it creates a guard to fetch the pipeline from the
  base branch. If not, no guard is needed.
  */
  PipelineEntity getPipelineEntity(PMSPipelineService pmsPipelineService, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier) {
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
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  public void validateInputSetForOldGitSync(PMSInputSetService inputSetService, PMSPipelineService pipelineService,
      InputSetEntity inputSetEntity, String pipelineBranch, String pipelineRepoID) {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();
    InputSetEntityType type = inputSetEntity.getInputSetEntityType();
    if (type.equals(InputSetEntityType.INPUT_SET)) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = validateInputSetForOldGitSync(pipelineService, accountId,
          orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, pipelineBranch, pipelineRepoID);
      if (errorWrapperDTO != null) {
        throw new InvalidInputSetException(
            "Some fields in the Input Set are invalid.", errorWrapperDTO, inputSetEntity);
      }
    } else {
      OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity);
    }
  }

  InputSetErrorWrapperDTOPMS validateInputSetForOldGitSync(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String yaml, String pipelineBranch,
      String pipelineRepoID) {
    validateIdentifyingFieldsInYAML(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    String pipelineYaml = getPipelineYamlForOldGitSyncFlow(pmsPipelineService, accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    return InputSetErrorsHelper.getErrorMap(pipelineYaml, yaml);
  }

  String getPipelineYamlForOldGitSyncFlow(PMSPipelineService pmsPipelineService, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String pipelineBranch, String pipelineRepoID) {
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
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
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
    if (identifier.length() > 63) {
      throw new InvalidRequestException("Input Set identifier length cannot be more that 63 characters.");
    }
    InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml, pipelineIdentifier);
    InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml, "inputSet", orgIdentifier, projectIdentifier);
  }
}
