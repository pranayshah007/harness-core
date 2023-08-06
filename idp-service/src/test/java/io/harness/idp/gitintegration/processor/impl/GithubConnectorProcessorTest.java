/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.impl;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.delegate.task.scm.ScmPushTaskResponseData;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.git.model.ListRemoteResult;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.*;
import io.harness.utils.NGFeatureFlagHelperService;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class GithubConnectorProcessorTest {
  @InjectMocks GithubConnectorProcessor githubConnectorProcessor;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Captor private ArgumentCaptor<String> stringCaptor;
  @Mock private Call call;
  AutoCloseable openMocks;

  private static final String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  private static final String USER_NAME = "test-username";
  private static final String EMAIL = "test-email@gmail.com";
  private static final String URL = "https://test-url";
  private static final String CONNECTOR_NAME = "test-connector-name";
  private static final String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  private static final String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";
  private static final String GITHUB_APP_APPLICATION_ID = "test-github-app-id";
  private static final String GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER = "test-github-private-key-secret-identifier";
  private static final String GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE = "test-github-private-key-decrypted-value";
  private static final String GITHUB_APP_INSTALLATION_ID = "test-github-installation-id";
  private static final String TEST_INTEGRATION_CONFIG = "test-config";
  private static final String TEST_HOST = "github.com";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidType() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(false, false, true, true, true);
    githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForSSHAuth() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(false, true, false, true, true);
    githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidAuthType() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(false, true, true, false, true);
    githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidIdentifier() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(false, true, true, true, false);
    githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetSecretsInfoForGithubUsernameAndTokenAuthHttpConnection() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(false, true, true, true, true);

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

    Map<String, BackstageEnvVariable> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());

    assertEquals(TOKEN_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.GITHUB_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.GITHUB_TOKEN, response.get(Constants.GITHUB_TOKEN).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetSecretsInfoForGithubUsernameAndTokenAuthWithGithubAppWithHttpConnection() {
    ConnectorDTO connectorDTO = getGithubConnectorDTO(true, true, true, true, true);

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
    Map<String, BackstageEnvVariable> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());

    assertEquals(
        GITHUB_APP_APPLICATION_ID, ((BackstageEnvConfigVariable) response.get(Constants.GITHUB_APP_ID)).getValue());
    assertEquals(Constants.GITHUB_APP_ID, response.get(Constants.GITHUB_APP_ID).getEnvName());
    assertEquals(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.GITHUB_APP_PRIVATE_KEY_REF)).getHarnessSecretIdentifier());
    assertEquals(Constants.GITHUB_APP_PRIVATE_KEY_REF, response.get(Constants.GITHUB_APP_PRIVATE_KEY_REF).getEnvName());
    assertEquals(TOKEN_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.GITHUB_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.GITHUB_TOKEN, response.get(Constants.GITHUB_TOKEN).getEnvName());
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateOrUpdateIntegrationConfigForGithubUsernameAndTokenAuthHttpConnection() {
    ConnectorInfoDTO connectorInfoDTO = getGithubConnectorDTO(false, true, true, true, true).getConnectorInfo();
    doNothing().when(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    MockedStatic<ConfigManagerUtils> mockRestUtilsConfigManagerUtils = mockStatic(ConfigManagerUtils.class);
    MockedStatic<GitIntegrationUtils> mockRestUtilsGitIntegrationUtils = mockStatic(GitIntegrationUtils.class);
    mockRestUtilsGitIntegrationUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn(TEST_INTEGRATION_CONFIG);
    mockRestUtilsGitIntegrationUtils.when(() -> GitIntegrationUtils.getHostForConnector(any())).thenReturn(TEST_HOST);
    githubConnectorProcessor.createOrUpdateIntegrationConfig(ACCOUNT_IDENTIFIER, connectorInfoDTO);
    verify(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    mockRestUtilsConfigManagerUtils.close();
    mockRestUtilsGitIntegrationUtils.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateOrUpdateIntegrationConfigForUsernameAndTokenAuthWithGithubAppWithHttpConnection() {
    ConnectorInfoDTO connectorInfoDTO = getGithubConnectorDTO(true, true, true, true, true).getConnectorInfo();
    doNothing().when(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    MockedStatic<ConfigManagerUtils> mockRestUtilsConfigManagerUtils = mockStatic(ConfigManagerUtils.class);
    MockedStatic<GitIntegrationUtils> mockRestUtilsGitIntegrationUtils = mockStatic(GitIntegrationUtils.class);
    mockRestUtilsGitIntegrationUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn(TEST_INTEGRATION_CONFIG);
    mockRestUtilsGitIntegrationUtils.when(() -> GitIntegrationUtils.getHostForConnector(any())).thenReturn(TEST_HOST);
    githubConnectorProcessor.createOrUpdateIntegrationConfig(ACCOUNT_IDENTIFIER, connectorInfoDTO);
    verify(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    mockRestUtilsConfigManagerUtils.close();
    mockRestUtilsGitIntegrationUtils.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetInfraConnectorType() {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    GithubConnectorDTO githubConnectorDTO = new GithubConnectorDTO();
    githubConnectorDTO.setExecuteOnDelegate(true);
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);
    String result = githubConnectorProcessor.getInfraConnectorType(connectorInfoDTO);
    assertEquals(CATALOG_INFRA_CONNECTOR_TYPE_PROXY, result);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetLocationTarget() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    String expectResult = "harness-core/blob/develop/harness-idp-entities";
    assertEquals(
        expectResult, githubConnectorProcessor.getLocationTarget(catalogConnectorInfo, "/harness-idp-entities"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPerformPushOperation() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    ConnectorDTO connectorDTO = getGithubConnectorDTO(true, true, true, true, true);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(TOKEN_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    MockedStatic<ConfigManagerUtils> mockedStaticConfigManagerUtils = Mockito.mockStatic(ConfigManagerUtils.class);
    mockedStaticConfigManagerUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn("");
    when(ngSecretService.getDecryptedSecretValue(any(), any(), any(), any())).thenReturn(decryptedSecretValue);
    MockedStatic<SourcePrincipalContextBuilder> mockedStaticContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    mockedStaticContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(new UserPrincipal(USER_NAME, EMAIL, USER_NAME, ACCOUNT_IDENTIFIER));
    MockedStatic<Files> mockedStaticFile = Mockito.mockStatic(Files.class);
    mockedStaticFile.when(() -> Files.readString(any())).thenReturn("sample content");
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(List.of(EncryptedDataDetail.builder().build()));
    ListRemoteResult listRemoteResult =
        ListRemoteResult.builder().remoteList(Map.of("refs/heads/develop", "develop")).build();
    CreateFileResponse createFileResponse = CreateFileResponse.newBuilder().setStatus(200).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitCommandExecutionResponse.builder().gitCommandResult(listRemoteResult).build())
        .thenReturn(ScmPushTaskResponseData.builder().createFileResponse(createFileResponse.toByteArray()).build());

    githubConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), true);
    verify(ngSecretService).getDecryptedSecretValue(any(), any(), any(), stringCaptor.capture());
    assertEquals(TOKEN_SECRET_IDENTIFIER, stringCaptor.getValue());

    mockRestStatic.close();
    mockedStaticConfigManagerUtils.close();
    mockedStaticContextBuilder.close();
    mockedStaticFile.close();
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPerformPushOperationThrowsException() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    ConnectorDTO connectorDTO = getGithubConnectorDTO(true, true, true, true, true);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(TOKEN_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    MockedStatic<ConfigManagerUtils> mockedStaticConfigManagerUtils = Mockito.mockStatic(ConfigManagerUtils.class);
    mockedStaticConfigManagerUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn("");
    when(ngSecretService.getDecryptedSecretValue(any(), any(), any(), any())).thenReturn(decryptedSecretValue);
    MockedStatic<SourcePrincipalContextBuilder> mockedStaticContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    mockedStaticContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(new UserPrincipal(USER_NAME, EMAIL, USER_NAME, ACCOUNT_IDENTIFIER));
    MockedStatic<Files> mockedStaticFile = Mockito.mockStatic(Files.class);
    mockedStaticFile.when(() -> Files.readString(any())).thenReturn("sample content");
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(List.of(EncryptedDataDetail.builder().build()));
    ListRemoteResult listRemoteResult =
        ListRemoteResult.builder().remoteList(Map.of("refs/heads/develop", "develop")).build();
    CreateFileResponse createFileResponse = CreateFileResponse.newBuilder().setStatus(300).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitCommandExecutionResponse.builder().gitCommandResult(listRemoteResult).build())
        .thenReturn(ScmPushTaskResponseData.builder().createFileResponse(createFileResponse.toByteArray()).build());

    githubConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorInfoThrowsException() throws IOException {
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Optional.empty())));
    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(call);
    githubConnectorProcessor.getConnectorInfo(ACCOUNT_IDENTIFIER, CONNECTOR_NAME);
  }
  @Test(expected = UnknownEnumTypeException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEncryptedDataDetailsThrowsException() {
    GitConfigDTO gitConfigDTO = new GitConfigDTO();
    gitConfigDTO.setGitAuthType(GitAuthType.SSH);
    githubConnectorProcessor.getEncryptedDataDetails(gitConfigDTO, BaseNGAccess.builder().build());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTaskTypeThrowsException() {
    githubConnectorProcessor.getTaskType("cgGit");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private ConnectorDTO getGithubConnectorDTO(
      boolean isGithubApp, boolean validType, boolean isHttpAuth, boolean validAuthType, boolean validIdentifier) {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier(validIdentifier ? TOKEN_SECRET_IDENTIFIER : "").build();
    SecretRefData secretRefDataGithubAppPrivateKeyRef =
        SecretRefData.builder().identifier(GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER).build();
    GithubAppSpecDTO githubAppSpecDTO = GithubAppSpecDTO.builder()
                                            .applicationId(GITHUB_APP_APPLICATION_ID)
                                            .installationId(GITHUB_APP_INSTALLATION_ID)
                                            .privateKeyRef(secretRefDataGithubAppPrivateKeyRef)
                                            .build();
    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().type(GithubApiAccessType.GITHUB_APP).spec(githubAppSpecDTO).build();
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    GithubHttpCredentialsDTO githubHttpCredentialsDTO =
        GithubHttpCredentialsDTO.builder()
            .type(validAuthType ? GithubHttpAuthenticationType.USERNAME_AND_TOKEN
                                : GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
            .httpCredentialsSpec(githubUsernameTokenDTO)
            .build();
    GithubAuthenticationDTO githubAuthenticationDTO = GithubAuthenticationDTO.builder()
                                                          .authType(isHttpAuth ? GitAuthType.HTTP : GitAuthType.SSH)
                                                          .credentials(githubHttpCredentialsDTO)
                                                          .build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(isGithubApp ? githubApiAccessDTO : null)
                                                .authentication(githubAuthenticationDTO)
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(null)
                                                .executeOnDelegate(true)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(validType ? ConnectorType.GITHUB : ConnectorType.GITLAB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
  }

  private CatalogConnectorInfo getCatalogConnectorInfo() {
    CatalogConnectorInfo catalogConnectorInfo = new CatalogConnectorInfo();
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier(CONNECTOR_NAME);
    connectorDetails.setType(ConnectorDetails.TypeEnum.GITHUB);
    catalogConnectorInfo.setConnector(connectorDetails);
    catalogConnectorInfo.setRepo("harness-core");
    catalogConnectorInfo.setBranch("develop");
    catalogConnectorInfo.setPath("/harness-idp-entities");
    return catalogConnectorInfo;
  }
}
