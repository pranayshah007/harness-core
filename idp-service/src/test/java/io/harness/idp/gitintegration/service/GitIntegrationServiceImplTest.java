/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.service;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.*;
import io.harness.delegate.beans.connector.scm.bitbucket.*;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.*;
import io.harness.encryption.SecretRefData;
import io.harness.idp.gitintegration.processor.impl.AzureRepoConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.BitbucketConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GitlabConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.List;
import java.util.Optional;
import org.apache.commons.math3.util.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationServiceImplTest {
  @InjectMocks GithubConnectorProcessor githubConnectorProcessor;
  @InjectMocks GitlabConnectorProcessor gitlabConnectorProcessor;
  @InjectMocks BitbucketConnectorProcessor bitbucketConnectorProcessor;
  @InjectMocks AzureRepoConnectorProcessor azureRepoConnectorProcessor;

  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService ngSecretService;

  String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  String USER_NAME = "test-username";
  String URL = "https://test-url";
  String CONNECTOR_NAME = "test-connector-name";
  String CONNECTOR_IDENTIFIER = "test-connector-identifier";
  String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";
  String PWD_SECRET_IDENTIFIER = "test-secret-identifier1";
  String GITHUB_APP_APPLICATION_ID = "test-github-app-id";

  String GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER = "test-github-private-key-secret-identifier";

  String GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE = "test-github-private-key-decrypted-value";

  String GITHUB_APP_INSTALLATION_ID = "test-github-installation-id";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetSecretsInfoForGithubUsernameAndTokenAuthHttpConnection() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(TOKEN_SECRET_IDENTIFIER).build();
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .build();
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(null)
                                                .authentication(githubAuthenticationDTO)
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(null)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(TOKEN_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    when(ngSecretService.getDecryptedSecretValue(ACCOUNT_IDENTIFIER, null, null, TOKEN_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(DECRYPTED_SECRET_VALUE, response.getSecond().get(0).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.getSecond().get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_TOKEN, response.getSecond().get(0).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForGitlabConnector() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(TOKEN_SECRET_IDENTIFIER).build();
    GitlabUsernameTokenDTO gitlabUsernameTokenDTO =
        GitlabUsernameTokenDTO.builder().tokenRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO = GitlabHttpCredentialsDTO.builder()
                                                            .type(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .httpCredentialsSpec(gitlabUsernameTokenDTO)
                                                            .build();
    GitlabAuthenticationDTO githubAuthenticationDTO =
        GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(gitlabHttpCredentialsDTO).build();
    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .apiAccess(null)
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .authentication(githubAuthenticationDTO)
                                                .delegateSelectors(null)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(gitlabConnectorDTO)
                                            .connectorType(ConnectorType.GITLAB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(TOKEN_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    when(ngSecretService.getDecryptedSecretValue(ACCOUNT_IDENTIFIER, null, null, TOKEN_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> response =
        gitlabConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(DECRYPTED_SECRET_VALUE, response.getSecond().get(0).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.getSecond().get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITLAB_TOKEN, response.getSecond().get(0).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForBitBucketConnector() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(PWD_SECRET_IDENTIFIER).build();
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder().passwordRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
        BitbucketHttpCredentialsDTO.builder()
            .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
            .httpCredentialsSpec(bitbucketUsernamePasswordDTO)
            .build();
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO = BitbucketAuthenticationDTO.builder()
                                                                .authType(GitAuthType.HTTP)
                                                                .credentials(bitbucketHttpCredentialsDTO)
                                                                .build();
    BitbucketConnectorDTO gitlabConnectorDTO = BitbucketConnectorDTO.builder()
                                                   .apiAccess(null)
                                                   .url(URL)
                                                   .connectionType(GitConnectionType.ACCOUNT)
                                                   .authentication(bitbucketAuthenticationDTO)
                                                   .delegateSelectors(null)
                                                   .executeOnDelegate(false)
                                                   .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(gitlabConnectorDTO)
                                            .connectorType(ConnectorType.BITBUCKET)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(PWD_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    when(ngSecretService.getDecryptedSecretValue(ACCOUNT_IDENTIFIER, null, null, PWD_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> response =
        bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(DECRYPTED_SECRET_VALUE, response.getSecond().get(0).getDecryptedValue());
    assertEquals(PWD_SECRET_IDENTIFIER, response.getSecond().get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.BITBUCKET_TOKEN, response.getSecond().get(0).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForAzureRepoConnector() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(TOKEN_SECRET_IDENTIFIER).build();
    AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
        AzureRepoUsernameTokenDTO.builder().tokenRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    AzureRepoHttpCredentialsDTO azureRepoHttpCredentialsDTO =
        AzureRepoHttpCredentialsDTO.builder()
            .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
            .httpCredentialsSpec(azureRepoUsernameTokenDTO)
            .build();
    AzureRepoAuthenticationDTO githubAuthenticationDTO = AzureRepoAuthenticationDTO.builder()
                                                             .authType(GitAuthType.HTTP)
                                                             .credentials(azureRepoHttpCredentialsDTO)
                                                             .build();
    AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                      .apiAccess(null)
                                                      .url(URL)
                                                      .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                      .authentication(githubAuthenticationDTO)
                                                      .delegateSelectors(null)
                                                      .executeOnDelegate(false)
                                                      .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(azureRepoConnectorDTO)
                                            .connectorType(ConnectorType.AZURE_REPO)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(TOKEN_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    when(ngSecretService.getDecryptedSecretValue(ACCOUNT_IDENTIFIER, null, null, TOKEN_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> response =
        azureRepoConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(DECRYPTED_SECRET_VALUE, response.getSecond().get(0).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.getSecond().get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.AZURE_REPO_TOKEN, response.getSecond().get(0).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetSecretsInfoForGithubUsernameAndTokenAuthWithGithubAppWithHttpConnection() {
    SecretRefData secretRefDataToken = SecretRefData.builder().identifier(TOKEN_SECRET_IDENTIFIER).build();
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(secretRefDataToken).username(USER_NAME).usernameRef(null).build();
    SecretRefData secretRefDataGithubAppPrivateKeyRef =
        SecretRefData.builder().identifier(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER).build();
    GithubAppSpecDTO githubAppSpecDTO = GithubAppSpecDTO.builder()
                                            .applicationId(GITHUB_APP_APPLICATION_ID)
                                            .installationId(GITHUB_APP_INSTALLATION_ID)
                                            .privateKeyRef(secretRefDataGithubAppPrivateKeyRef)
                                            .build();
    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().type(GithubApiAccessType.GITHUB_APP).spec(githubAppSpecDTO).build();
    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .build();
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(null)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValueToken = DecryptedSecretValue.builder()
                                                         .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                         .identifier(TOKEN_SECRET_IDENTIFIER)
                                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                         .orgIdentifier(null)
                                                         .projectIdentifier(null)
                                                         .build();
    DecryptedSecretValue decryptedSecretValuePrivateRef = DecryptedSecretValue.builder()
                                                              .decryptedValue(GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE)
                                                              .identifier(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER)
                                                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                              .orgIdentifier(null)
                                                              .projectIdentifier(null)
                                                              .build();
    when(ngSecretService.getDecryptedSecretValue(any(), any(), any(), any()))
        .thenReturn(decryptedSecretValuePrivateRef)
        .thenReturn(decryptedSecretValueToken);

    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(GITHUB_APP_APPLICATION_ID, response.getSecond().get(0).getDecryptedValue());
    assertEquals(null, response.getSecond().get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_APP_ID, response.getSecond().get(0).getEnvName());
    assertEquals(GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE, response.getSecond().get(1).getDecryptedValue());
    assertEquals(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER, response.getSecond().get(1).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_APP_PRIVATE_KEY_REF, response.getSecond().get(1).getEnvName());
    assertEquals(DECRYPTED_SECRET_VALUE, response.getSecond().get(2).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.getSecond().get(2).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_TOKEN, response.getSecond().get(2).getEnvName());
    mockRestStatic.close();
  }
}