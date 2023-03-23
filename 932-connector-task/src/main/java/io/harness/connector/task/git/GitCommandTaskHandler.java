/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.git;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.delegate.beans.git.GitCommandExecutionResponse.GitCommandStatus.SUCCESS;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.impl.ScmResponseStatusUtils.convertScmStatusCodeToErrorCode;
import static io.harness.impl.ScmResponseStatusUtils.formatErrorMessage;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.stripStart;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequestDTO;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ManagerExecutable;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.eraro.ErrorCode;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExplanationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.git.GitClientHelper;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmClient;
import io.harness.service.ScmServiceClient;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DX)
public class GitCommandTaskHandler {
  @Inject private NGGitService gitService;
  @Inject private GithubService gitHubService;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;
  @Inject private ExceptionManager exceptionManager;
  @Inject(optional = true) private ScmClient scmClient;

  public ConnectorValidationResult validateGitCredentials(GitConfigDTO gitConnector, ScmConnector scmConnector,
      String accountIdentifier, SshSessionConfig sshSessionConfig) {
    GitCommandExecutionResponse delegateResponseData;
    try {
      delegateResponseData = (GitCommandExecutionResponse) handleValidateTask(
          gitConnector, scmConnector, accountIdentifier, sshSessionConfig);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(delegateResponseData.getConnectorValidationResult().getTestedAt())
        .build();
  }

  public DelegateResponseData handleValidateTask(
      GitConfigDTO gitConfig, ScmConnector scmConnector, String accountId, SshSessionConfig sshSessionConfig) {
    log.info("Processing Git command: VALIDATE");
    handleGitValidation(gitConfig, accountId, sshSessionConfig);
    Boolean executeOnDelegate = Boolean.TRUE;
    if (gitConfig instanceof ManagerExecutable) {
      executeOnDelegate = ((ManagerExecutable) gitConfig).getExecuteOnDelegate();
    }

    handleApiAccessValidation(scmConnector, executeOnDelegate);
    return GitCommandExecutionResponse.builder()
        .gitCommandStatus(SUCCESS)
        .connectorValidationResult(ConnectorValidationResult.builder()
                                       .testedAt(System.currentTimeMillis())
                                       .status(ConnectivityStatus.SUCCESS)
                                       .build())
        .build();
  }

  private void handleGitValidation(GitConfigDTO gitConfig, String accountId, SshSessionConfig sshSessionConfig) {
    if (gitConfig.getGitConnectionType() == GitConnectionType.ACCOUNT
        || gitConfig.getGitConnectionType() == GitConnectionType.PROJECT) {
      if (isNotEmpty(gitConfig.getValidationRepo())) {
        String url = format("%s/%s", stripEnd(gitConfig.getUrl(), "/"), stripStart(gitConfig.getValidationRepo(), "/"));
        gitConfig.setUrl(url);
      } else {
        return;
      }
    }
    gitService.validateOrThrow(gitConfig, accountId, sshSessionConfig);
  }

  private void handleApiAccessValidation(ScmConnector scmConnector, Boolean executeOnDelegate) {
    if (!GitApiAccessDecryptionHelper.hasApiAccess(scmConnector)) {
      return;
    }

    if (scmConnector instanceof GithubConnectorDTO) {
      GithubConnectorDTO gitHubConnector = (GithubConnectorDTO) scmConnector;
      if (gitHubConnector.getApiAccess().getType() == GITHUB_APP) {
        validateGitHubApp(gitHubConnector);
        return;
      }
    }

    GetUserReposResponse reposResponse;
    PageRequestDTO pageRequest = PageRequestDTO.builder().pageIndex(0).build();
    try {
      if (executeOnDelegate == Boolean.FALSE) {
        reposResponse = scmClient.getUserRepos(scmConnector, pageRequest);
      } else {
        reposResponse = scmDelegateClient.processScmRequest(
            c -> scmServiceClient.getUserRepos(scmConnector, pageRequest, SCMGrpc.newBlockingStub(c)));
      }
    } catch (InvalidRequestException e) {
      throw SCMRuntimeException.builder().message(e.getMessage()).errorCode(ErrorCode.INVALID_REQUEST).build();
    } catch (Exception e) {
      throw SCMRuntimeException.builder().message(e.getMessage()).errorCode(ErrorCode.UNEXPECTED).cause(e).build();
    }
    if (reposResponse != null && reposResponse.getStatus() > 300) {
      String errorMessage = reposResponse.getError();
      if (isBlank(errorMessage)) {
        errorMessage = formatErrorMessage(reposResponse.getStatus());
      }
      log.error(
          String.format("Received status %s from scm provider, error: %s", reposResponse.getStatus(), errorMessage));

      ErrorCode errorCode = convertScmStatusCodeToErrorCode(reposResponse.getStatus());
      throw SCMRuntimeException.builder().errorCode(errorCode).message(errorMessage).build();
    }

    // AzureRepo returns an error with code 203
    if (reposResponse != null && scmConnector instanceof AzureRepoConnectorDTO && reposResponse.getStatus() == 203) {
      ErrorCode errorCode = convertScmStatusCodeToErrorCode(reposResponse.getStatus());
      throw new ExplanationException("Invalid API Access Token",
          SCMRuntimeException.builder().errorCode(errorCode).message(reposResponse.getError()).build());
    }
  }

  private void validateGitHubApp(GithubConnectorDTO gitHubConnector) {
    GithubApiAccessDTO apiAccess = gitHubConnector.getApiAccess();
    GithubAppSpecDTO apiAccessDTO = (GithubAppSpecDTO) apiAccess.getSpec();
    try {
      gitHubService.getToken(GithubAppConfig.builder()
                                 .appId(getSecretAsStringFromPlainTextOrSecretRef(
                                     apiAccessDTO.getApplicationId(), apiAccessDTO.getApplicationIdRef()))
                                 .installationId(getSecretAsStringFromPlainTextOrSecretRef(
                                     apiAccessDTO.getInstallationId(), apiAccessDTO.getInstallationIdRef()))
                                 .privateKey(String.valueOf(apiAccessDTO.getPrivateKeyRef().getDecryptedValue()))
                                 .githubUrl(GitClientHelper.getGithubApiURL(gitHubConnector.getUrl()))
                                 .build());
    } catch (Exception e) {
      throw SCMRuntimeException.builder()
          .errorCode(ErrorCode.SCM_UNAUTHORIZED)
          .cause(e.getCause())
          .message(e.getCause().getLocalizedMessage())
          .build();
    }
  }
}
