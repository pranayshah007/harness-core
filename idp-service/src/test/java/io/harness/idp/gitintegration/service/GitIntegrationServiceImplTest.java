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
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.idp.gitintegration.GitIntegrationConstants;
import io.harness.idp.gitintegration.implementation.GithubConnectorProcessor;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.List;
import java.util.Optional;
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

  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService ngSecretService;

  String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  String USER_NAME = "test-username";
  String URL = "https://test-url";
  String CONNECTOR_NAME = "test-connector-name";
  String CONNECTOR_IDENTIFIER = "test-connector-identifier";
  String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";
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

    List<EnvironmentSecret> response =
        githubConnectorProcessor.getConnectorSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(DECRYPTED_SECRET_VALUE, response.get(0).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_TOKEN, response.get(0).getEnvName());
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

    List<EnvironmentSecret> response =
        githubConnectorProcessor.getConnectorSecretsInfo(ACCOUNT_IDENTIFIER, null, null, CONNECTOR_IDENTIFIER);

    assertEquals(GITHUB_APP_APPLICATION_ID, response.get(0).getDecryptedValue());
    assertEquals(null, response.get(0).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_APP_ID, response.get(0).getEnvName());
    assertEquals(GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE, response.get(1).getDecryptedValue());
    assertEquals(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER, response.get(1).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_APP_PRIVATE_KEY_REF, response.get(1).getEnvName());
    assertEquals(DECRYPTED_SECRET_VALUE, response.get(2).getDecryptedValue());
    assertEquals(TOKEN_SECRET_IDENTIFIER, response.get(2).getSecretIdentifier());
    assertEquals(GitIntegrationConstants.GITHUB_TOKEN, response.get(2).getEnvName());
    mockRestStatic.close();
  }
}