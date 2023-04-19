/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType.TOKEN;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.git.GitCommandTaskHandler;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExplanationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.runtime.JGitRuntimeException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;
import io.harness.shell.SshSessionConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class GitCommandTaskHandlerTest extends CategoryTest {
  @Mock private NGGitService gitService;
  @Mock private GithubService gitHubService;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private ScmDelegateClient scmDelegateClient;
  @Mock private ScmServiceClient scmServiceClient;

  @Spy @InjectMocks GitCommandTaskHandler gitCommandTaskHandler;
  @Spy @InjectMocks ExceptionManager exceptionManager;

  private static final long SIMULATED_REQUEST_TIME_MILLIS = 1609459200000L;
  private static final String ACCOUNT_IDENTIFIER = generateUuid();
  private static final String SIMULATED_EXCEPTION_MESSAGE = generateUuid();
  private static final String TEST_GIT_REPO_URL = "https://www.github.com/dummy-org/dummy-repo";
  private static final String TEST_STRING_INPUT = generateUuid();
  private SshSessionConfig sshSessionConfig;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGitCredentials() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().build();
    ScmConnector connector = GitlabConnectorDTO.builder().build();
    GitCommandExecutionResponse response = mock(GitCommandExecutionResponse.class, RETURNS_DEEP_STUBS);
    when(response.getConnectorValidationResult().getTestedAt()).thenReturn(SIMULATED_REQUEST_TIME_MILLIS);
    doReturn(response)
        .when(gitCommandTaskHandler)
        .handleValidateTask(
            any(GitConfigDTO.class), any(ScmConnector.class), any(String.class), nullable(SshSessionConfig.class));

    ConnectorValidationResult validationResult =
        gitCommandTaskHandler.validateGitCredentials(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(validationResult.getTestedAt()).isEqualTo(SIMULATED_REQUEST_TIME_MILLIS);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGitCredentialsWhenException() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().build();
    ScmConnector connector = GitlabConnectorDTO.builder().build();
    doThrow(new JGitRuntimeException(SIMULATED_EXCEPTION_MESSAGE))
        .when(gitCommandTaskHandler)
        .handleValidateTask(
            any(GitConfigDTO.class), any(ScmConnector.class), any(String.class), nullable(SshSessionConfig.class));

    gitCommandTaskHandler.validateGitCredentials(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidateTask() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build();
    ScmConnector connector = GithubConnectorDTO.builder()
                                 .url(TEST_GIT_REPO_URL)
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.GITHUB_APP)
                                                .spec(GithubAppSpecDTO.builder()
                                                          .applicationId(TEST_STRING_INPUT)
                                                          .installationId(TEST_STRING_INPUT)
                                                          .privateKeyRef(new SecretRefData(TEST_STRING_INPUT,
                                                              Scope.ACCOUNT, TEST_STRING_INPUT.toCharArray()))
                                                          .build())
                                                .build())
                                 .build();
    doNothing()
        .when(gitService)
        .validateOrThrow(any(GitConfigDTO.class), any(String.class), any(SshSessionConfig.class));
    doReturn(TEST_STRING_INPUT).when(gitHubService).getToken(any(GithubAppConfig.class));

    GitCommandExecutionResponse response = (GitCommandExecutionResponse) gitCommandTaskHandler.handleValidateTask(
        gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    assertThat(response.getGitCommandStatus()).isEqualTo(GitCommandExecutionResponse.GitCommandStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidateGithubAppWithException() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build();
    ScmConnector connector = GithubConnectorDTO.builder()
                                 .url(TEST_GIT_REPO_URL)
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.GITHUB_APP)
                                                .spec(GithubAppSpecDTO.builder()
                                                          .applicationId(TEST_STRING_INPUT)
                                                          .installationId(TEST_STRING_INPUT)
                                                          .privateKeyRef(new SecretRefData(TEST_STRING_INPUT,
                                                              Scope.ACCOUNT, TEST_STRING_INPUT.toCharArray()))
                                                          .build())
                                                .build())
                                 .build();
    doThrow(new RuntimeException(SIMULATED_EXCEPTION_MESSAGE, new SCMRuntimeException(SIMULATED_EXCEPTION_MESSAGE)))
        .when(gitHubService)
        .getToken(any(GithubAppConfig.class));
    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(SCMRuntimeException.class);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAzureRepoApiCredentialsWhenException() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final String username = "username";
    final String validationRepo = "validationRepo";
    final String invalid = "XYZ";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .username(username)
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .build())
                             .build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(invalid)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();
    GitConfigDTO gitConfig = GitConfigDTO.builder().build();
    ScmConnector connector = azureRepoConnectorDTO;
    GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().setStatus(203).build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());
    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(ExplanationException.class)
        .hasMessage("Invalid API Access Token");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidateTaskGivenScmConnectorWithoutApiAccess() {
    GitConfigDTO gitConfig =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).validationRepo(TEST_STRING_INPUT).build();
    ScmConnector connector = GithubConnectorDTO.builder().build();

    GitCommandExecutionResponse response = (GitCommandExecutionResponse) gitCommandTaskHandler.handleValidateTask(
        gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    assertThat(response.getGitCommandStatus()).isEqualTo(GitCommandExecutionResponse.GitCommandStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidateTaskHandleApiAccessValidation() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        GitlabConnectorDTO.builder()
            .apiAccess(GitlabApiAccessDTO.builder().spec(GitlabTokenSpecDTO.builder().build()).build())
            .build();
    GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());

    GitCommandExecutionResponse response = (GitCommandExecutionResponse) gitCommandTaskHandler.handleValidateTask(
        gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    assertThat(response.getGitCommandStatus()).isEqualTo(GitCommandExecutionResponse.GitCommandStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testApiAccessValidationGivenExceptionInScmRequest() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        GitlabConnectorDTO.builder()
            .apiAccess(GitlabApiAccessDTO.builder().spec(GitlabTokenSpecDTO.builder().build()).build())
            .build();
    doThrow(new SCMRuntimeException(SIMULATED_EXCEPTION_MESSAGE)).when(scmDelegateClient).processScmRequest(any());
    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(SCMRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testApiAccessValidationGivenErrorInRepoResponse() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        GitlabConnectorDTO.builder()
            .apiAccess(GitlabApiAccessDTO.builder().spec(GitlabTokenSpecDTO.builder().build()).build())
            .build();
    GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().setStatus(400).build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());

    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(SCMRuntimeException.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInvalidScmRequest() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        GitlabConnectorDTO.builder()
            .apiAccess(GitlabApiAccessDTO.builder().spec(GitlabTokenSpecDTO.builder().build()).build())
            .build();
    doThrow(new InvalidRequestException(SIMULATED_EXCEPTION_MESSAGE)).when(scmDelegateClient).processScmRequest(any());
    try {
      gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    } catch (Exception e) {
      assertThat(e instanceof SCMRuntimeException).isTrue();
      assertThat(e.getMessage().equals(SIMULATED_EXCEPTION_MESSAGE)).isTrue();
    }
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testValidateTaskHandleApiAccessValidationBitbucket() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        BitbucketConnectorDTO.builder()
            .apiAccess(
                BitbucketApiAccessDTO.builder().spec(BitbucketUsernameTokenApiAccessDTO.builder().build()).build())
            .build();
    GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());

    GitCommandExecutionResponse response = (GitCommandExecutionResponse) gitCommandTaskHandler.handleValidateTask(
        gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    assertThat(response.getGitCommandStatus()).isEqualTo(GitCommandExecutionResponse.GitCommandStatus.SUCCESS);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testApiAccessValidationGivenExceptionInScmRequestBitbucket() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        BitbucketConnectorDTO.builder()
            .apiAccess(
                BitbucketApiAccessDTO.builder().spec(BitbucketUsernameTokenApiAccessDTO.builder().build()).build())
            .build();
    doThrow(new SCMRuntimeException(SIMULATED_EXCEPTION_MESSAGE)).when(scmDelegateClient).processScmRequest(any());
    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(SCMRuntimeException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testApiAccessValidationGivenErrorInRepoResponseBitbucket() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        BitbucketConnectorDTO.builder()
            .apiAccess(
                BitbucketApiAccessDTO.builder().spec(BitbucketUsernameTokenApiAccessDTO.builder().build()).build())
            .build();
    GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().setStatus(400).build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());

    assertThatThrownBy(
        () -> gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig))
        .isInstanceOf(SCMRuntimeException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testInvalidScmRequestBitbucket() {
    GitConfigDTO gitConfig = GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).build();
    ScmConnector connector =
        BitbucketConnectorDTO.builder()
            .apiAccess(
                BitbucketApiAccessDTO.builder().spec(BitbucketUsernameTokenApiAccessDTO.builder().build()).build())
            .build();
    doThrow(new InvalidRequestException(SIMULATED_EXCEPTION_MESSAGE)).when(scmDelegateClient).processScmRequest(any());
    try {
      gitCommandTaskHandler.handleValidateTask(gitConfig, connector, ACCOUNT_IDENTIFIER, sshSessionConfig);
    } catch (Exception e) {
      assertThat(e instanceof SCMRuntimeException).isTrue();
      assertThat(e.getMessage().equals(SIMULATED_EXCEPTION_MESSAGE)).isTrue();
    }
  }
}
