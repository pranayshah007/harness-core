/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static io.harness.connector.helper.GitApiAccessDecryptionHelper.hasApiAccess;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.TaskType.NG_GIT_COMMAND;

import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ManagerExecutable;
import io.harness.connector.validator.AbstractConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.delegate.beans.git.GitCommandParams;
import io.harness.delegate.beans.git.GitCommandType;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractGitConnectorValidator extends AbstractConnectorValidator {
  @Inject GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final GitConfigDTO gitConfig = getGitConfigFromConnectorConfig(connectorConfig);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfig, accountIdentifier, orgIdentifier, projectIdentifier);
    NGAccess ngAccess = getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    List<EncryptedDataDetail> authenticationEncryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfig, sshKeySpecDTO, ngAccess);
    if (isNotEmpty(authenticationEncryptedDataDetails)) {
      encryptedDataDetails.addAll(authenticationEncryptedDataDetails);
    }
    ScmConnector scmConnector = (ScmConnector) connectorConfig;

    if (hasApiAccess(scmConnector)) {
      List<EncryptedDataDetail> apiAccessEncryptedDataDetail =
          gitConfigAuthenticationInfoHelper.getApiAccessEncryptedDataDetail(scmConnector, ngAccess);
      if (isNotEmpty(apiAccessEncryptedDataDetail)) {
        encryptedDataDetails.addAll(apiAccessEncryptedDataDetail);
      }
    }

    return GitCommandParams.builder()
        .gitConfig(gitConfig)
        .scmConnector(scmConnector)
        .sshKeySpecDTO(sshKeySpecDTO)
        .gitCommandType(GitCommandType.VALIDATE)
        .encryptionDetails(encryptedDataDetails)
        .build();
  }

  private NGAccess getNgAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public abstract GitConfigDTO getGitConfigFromConnectorConfig(ConnectorConfigDTO connectorConfig);

  @Override
  public String getTaskType() {
    return NG_GIT_COMMAND.name();
  }

  public void validateFieldsPresent(GitConfigDTO gitConfig) {
    switch (gitConfig.getGitAuthType()) {
      case HTTP:
        GitHTTPAuthenticationDTO gitAuthenticationDTO = (GitHTTPAuthenticationDTO) gitConfig.getGitAuth();
        validateRequiredFieldsPresent(
            gitAuthenticationDTO.getPasswordRef(), gitConfig.getUrl(), gitConfig.getGitConnectionType());
        break;
      case SSH:
        GitSSHAuthenticationDTO gitSSHAuthenticationDTO = (GitSSHAuthenticationDTO) gitConfig.getGitAuth();
        validateRequiredFieldsPresent(gitSSHAuthenticationDTO.getEncryptedSshKey());
        break;
      default:
        throw new UnknownEnumTypeException("Git Authentication Type",
            gitConfig.getGitAuthType() == null ? null : gitConfig.getGitAuthType().getDisplayName());
    }
  }

  public ConnectorValidationResult buildConnectorValidationResult(
      GitCommandExecutionResponse gitCommandExecutionResponse, String taskId) {
    String delegateId = null;
    if (gitCommandExecutionResponse.getDelegateMetaInfo() != null) {
      delegateId = gitCommandExecutionResponse.getDelegateMetaInfo().getId();
    }
    ConnectorValidationResult validationResult = gitCommandExecutionResponse.getConnectorValidationResult();
    if (validationResult != null) {
      validationResult.setDelegateId(delegateId);
    }
    validationResult.setTaskId(taskId);
    return validationResult;
  }

  private void validateRequiredFieldsPresent(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required field is empty."));
  }

  public ConnectorValidationResult validate(ConnectorConfigDTO connectorConfigDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    final GitConfigDTO gitConfig = getGitConfigFromConnectorConfig(connectorConfigDTO);
    validateFieldsPresent(gitConfig);
    Boolean executeOnDelegate = Boolean.TRUE;
    if (connectorConfigDTO instanceof ManagerExecutable) {
      executeOnDelegate = ((ManagerExecutable) connectorConfigDTO).getExecuteOnDelegate();
    }
    if (executeOnDelegate == Boolean.FALSE) {
      return super.validateConnectorViaManager(
          connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    } else {
      var responseEntry = super.validateConnectorReturnPair(
          connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      GitCommandExecutionResponse gitRespones = (GitCommandExecutionResponse) responseEntry.getValue();
      return buildConnectorValidationResult(gitRespones, responseEntry.getKey());
    }
  }
}
