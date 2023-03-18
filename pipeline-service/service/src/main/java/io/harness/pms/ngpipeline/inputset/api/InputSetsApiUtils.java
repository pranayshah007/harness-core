/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.getMoveConfigType;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.utils.PipelineYamlHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.pipeline.v1.model.FQNtoError;
import io.harness.spec.server.pipeline.v1.model.GitCreateDetails;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetError;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetGitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetsApiUtils {
  @Inject private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject private final NGSettingsClient ngSettingsClient;

  public InputSetResponseBody getInputSetResponse(InputSetEntity inputSetEntity) {
    InputSetResponseBody responseBody = new InputSetResponseBody();
    responseBody.setInputSetYaml(inputSetEntity.getYaml());
    responseBody.setIdentifier(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setOrg(inputSetEntity.getOrgIdentifier());
    responseBody.setProject(inputSetEntity.getProjectIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(ApiUtils.getTags(inputSetEntity.getTags()));
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
    responseBody.setIdentifier(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setOrg(inputSetEntity.getOrgIdentifier());
    responseBody.setProject(inputSetEntity.getProjectIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(ApiUtils.getTags(inputSetEntity.getTags()));
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
        .identifier(createRequestBody.getIdentifier())
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
        .identifier(updateRequestBody.getIdentifier())
        .name(updateRequestBody.getName())
        .yaml(updateRequestBody.getInputSetYaml())
        .description(updateRequestBody.getDescription())
        .tags(updateRequestBody.getTags())
        .build();
  }

  public String inputSetVersion(String accountId, String yaml) {
    boolean isYamlSimplificationEnabled = pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CI_YAML_VERSIONING);
    return PipelineYamlHelper.getVersion(yaml, isYamlSimplificationEnabled);
  }

  public boolean isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(String accountId) {
    String isGitClientEnabledString =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(
                GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS, accountId, null, null))
            .getValue();
    return GitSyncConstants.TRUE_VALUE.equals(isGitClientEnabledString);
  }

  public static InputSetMoveConfigOperationDTO buildMoveConfigOperationDTO(GitMoveDetails gitDetails,
      io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType moveConfigOperationType,
      String pipelineIdentifier) {
    return InputSetMoveConfigOperationDTO.builder()
        .repoName(gitDetails.getRepoName())
        .branch(gitDetails.getBranchName())
        .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
        .connectorRef(gitDetails.getConnectorRef())
        .baseBranch(gitDetails.getBaseBranch())
        .commitMessage(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .filePath(gitDetails.getFilePath())
        .pipelineIdentifier(pipelineIdentifier)
        .build();
  }
}
