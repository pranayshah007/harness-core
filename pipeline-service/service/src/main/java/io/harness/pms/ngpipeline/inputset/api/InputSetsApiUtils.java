/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.service.InputSetValidationHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.spec.server.pipeline.v1.model.FQNtoError;
import io.harness.spec.server.pipeline.v1.model.GitCreateDetails;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetError;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetGitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetsApiUtils {
  public InputSetResponseBody getInputSetResponse(InputSetEntity inputSetEntity) {
    InputSetResponseBody responseBody = new InputSetResponseBody();
    responseBody.setInputSetYaml(inputSetEntity.getYaml());
    responseBody.setSlug(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setOrg(inputSetEntity.getOrgIdentifier());
    responseBody.setProject(inputSetEntity.getProjectIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(getTags(inputSetEntity.getTags()));
    responseBody.setGitDetails(getGitDetails(inputSetEntity));
    responseBody.setCreated(inputSetEntity.getCreatedAt());
    responseBody.setUpdated(inputSetEntity.getLastUpdatedAt());
    responseBody.setErrorDetails(new InputSetErrorDetails().valid(true));
    return responseBody;
  }

  public InputSetResponseBody getInputSetResponseWithError(
      InputSetEntity inputSetEntity, InputSetErrorWrapperDTOPMS errorWrapperDTO) {
    InputSetResponseBody responseBody = new InputSetResponseBody();
    responseBody.setInputSetYaml(inputSetEntity.getYaml());
    responseBody.setSlug(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setOrg(inputSetEntity.getOrgIdentifier());
    responseBody.setProject(inputSetEntity.getProjectIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(getTags(inputSetEntity.getTags()));
    responseBody.setGitDetails(getGitDetails(inputSetEntity));
    responseBody.setCreated(inputSetEntity.getCreatedAt());
    responseBody.setUpdated(inputSetEntity.getLastUpdatedAt());
    InputSetErrorDetails errorDetails = new InputSetErrorDetails();
    errorDetails.setValid(false);
    errorDetails.setMessage("Some fields in the Input Set are invalid.");
    errorDetails.setOutdated(inputSetEntity.getIsInvalid());
    errorDetails.setErrorPipelineYaml(errorWrapperDTO.getErrorPipelineYaml());
    errorDetails.setInvalidRefs(errorWrapperDTO.getInvalidInputSetReferences());
    errorDetails.setFqnErrors(getFQNErrors(errorWrapperDTO));
    responseBody.setErrorDetails(errorDetails);
    return responseBody;
  }

  public Map<String, String> getTags(List<NGTag> ngTags) {
    if (isEmpty(ngTags)) {
      return null;
    }
    Map<String, String> tags = new HashMap<>();
    for (NGTag ngTag : ngTags) {
      tags.put(ngTag.getKey(), ngTag.getValue());
    }
    return tags;
  }

  public GitDetails getGitDetails(InputSetEntity inputSetEntity) {
    GitDetails gitDetails = new GitDetails();
    if (inputSetEntity.getStoreType() == null) {
      gitDetails.setBranchName(inputSetEntity.getBranch());
      gitDetails.setObjectId(inputSetEntity.getObjectIdOfYaml());
      gitDetails.setRepoName(inputSetEntity.getRepo());
      gitDetails.setRepoUrl(inputSetEntity.getRepoURL());
      gitDetails.setFilePath(inputSetEntity.getFilePath());
      return gitDetails;
    } else if (inputSetEntity.getStoreType() == StoreType.REMOTE) {
      EntityGitDetails entityGitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
      gitDetails.setBranchName(entityGitDetails.getBranch());
      gitDetails.setObjectId(entityGitDetails.getObjectId());
      gitDetails.setRepoName(entityGitDetails.getRepoName());
      gitDetails.setRepoUrl(entityGitDetails.getRepoUrl());
      gitDetails.setFilePath(entityGitDetails.getFilePath());
      gitDetails.setFileUrl(entityGitDetails.getFileUrl());
      gitDetails.setCommitId(entityGitDetails.getCommitId());
      return gitDetails;
    }
    return null;
  }

  public List<FQNtoError> getFQNErrors(InputSetErrorWrapperDTOPMS errorWrapperDTO) {
    List<FQNtoError> fqNtoErrors = new ArrayList<>();
    Set<String> keys = errorWrapperDTO.getUuidToErrorResponseMap().keySet();
    for (String key : keys) {
      InputSetErrorResponseDTOPMS value = errorWrapperDTO.getUuidToErrorResponseMap().get(key);
      FQNtoError fqNtoError = new FQNtoError();
      fqNtoError.fqn(key);
      fqNtoError.errors(getErrors(value.getErrors()));
      fqNtoErrors.add(fqNtoError);
    }
    return fqNtoErrors;
  }

  public List<InputSetError> getErrors(List<InputSetErrorDTOPMS> errorDTOPMS) {
    List<InputSetError> errors = new ArrayList<>();
    for (InputSetErrorDTOPMS errorDTO : errorDTOPMS) {
      InputSetError inputSetError = new InputSetError();
      inputSetError.setMessage(errorDTO.getMessage());
      inputSetError.setIdentifierOfErrorSource(errorDTO.getIdentifierOfErrorSource());
      inputSetError.setFieldName(errorDTO.getFieldName());
      errors.add(inputSetError);
    }
    return errors;
  }

  public static GitEntityInfo populateGitCreateDetails(GitCreateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .connectorRef(gitDetails.getConnectorRef())
        .storeType(StoreType.getFromStringOrNull(gitDetails.getStoreType().toString()))
        .repoName(gitDetails.getRepoName())
        .build();
  }

  public static GitEntityInfo populateGitUpdateDetails(InputSetGitUpdateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .lastCommitId(gitDetails.getLastCommitId())
        .lastObjectId(gitDetails.getLastObjectId())
        .parentEntityConnectorRef(gitDetails.getParentEntityConnectorRef())
        .parentEntityRepoName(gitDetails.getParentEntityRepoName())
        .build();
  }

  public static InputSetRequestInfoDTO mapCreateToRequestInfoDTO(InputSetCreateRequestBody createRequestBody) {
    if (createRequestBody == null) {
      throw new InvalidRequestException("Create Request Body cannot be null.");
    }
    return InputSetRequestInfoDTO.builder()
        .identifier(createRequestBody.getSlug())
        .name(createRequestBody.getName())
        .yaml(createRequestBody.getInputSetYaml())
        .description(createRequestBody.getDescription())
        .tags(createRequestBody.getTags())
        .build();
  }

  public static InputSetRequestInfoDTO mapUpdateToRequestInfoDTO(InputSetUpdateRequestBody updateRequestBody) {
    if (updateRequestBody == null) {
      throw new InvalidRequestException("Update Request Body cannot be null.");
    }
    return InputSetRequestInfoDTO.builder()
        .identifier(updateRequestBody.getSlug())
        .name(updateRequestBody.getName())
        .yaml(updateRequestBody.getInputSetYaml())
        .description(updateRequestBody.getDescription())
        .tags(updateRequestBody.getTags())
        .build();
  }

  public String getPipelineYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, PMSPipelineService pipelineService,
      GitSyncSdkService gitSyncSdkService) {
    boolean isOldGitSyncFlow = gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
    final String pipelineYaml;
    if (isOldGitSyncFlow) {
      pipelineYaml = InputSetValidationHelper.getPipelineYamlForOldGitSyncFlow(pipelineService, accountId,
          orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      PipelineEntity pipelineEntity = InputSetValidationHelper.getPipelineEntity(
          pipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      pipelineYaml = pipelineEntity.getYaml();
    }
    return pipelineYaml;
  }
}
