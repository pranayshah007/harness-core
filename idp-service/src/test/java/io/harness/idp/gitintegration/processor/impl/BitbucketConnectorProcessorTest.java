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
import static org.mockito.Mockito.mockStatic;

import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.*;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2Impl;
import io.harness.git.model.ListRemoteResult;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spec.server.idp.v1.model.*;
import io.harness.utils.NGFeatureFlagHelperService;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

public class BitbucketConnectorProcessorTest {
  @InjectMocks BitbucketConnectorProcessor bitbucketConnectorProcessor;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Captor private ArgumentCaptor<String> stringCaptor;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private SecretManagerClientService ngSecretServiceNonPrivileged;
  @Mock private GitClientV2Impl gitClientV2;
  @Mock private HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoService;
  AutoCloseable openMocks;
  private static final String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  private static final String USER_NAME = "test-username";
  private static final String EMAIL = "test-email@gmail.com";
  private static final String URL = "https://test-url";
  private static final String CONNECTOR_NAME = "test-connector-name";
  private static final String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  private static final String PWD_SECRET_IDENTIFIER = "test-secret-identifier1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidType() {
    ConnectorDTO connectorDTO = getBitbucketConnectorDTO(false, true, true, true);
    bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidSecretIdentifier() {
    ConnectorDTO connectorDTO = getBitbucketConnectorDTO(true, false, true, true);
    bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForValidUserNameRefAndHasApiAccess() {
    ConnectorDTO connectorDTO = getBitbucketConnectorDTO(true, true, true, true);
    MockedStatic<ConfigManagerUtils> mockedStaticConfigManagerUtils = Mockito.mockStatic(ConfigManagerUtils.class);
    mockedStaticConfigManagerUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn("");
    Map<String, BackstageEnvVariable> response =
        bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
    assertEquals(PWD_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.BITBUCKET_API_ACCESS_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.BITBUCKET_API_ACCESS_TOKEN, response.get(Constants.BITBUCKET_API_ACCESS_TOKEN).getEnvName());
    assertEquals(USER_NAME,
        ((BackstageEnvSecretVariable) response.get(Constants.BITBUCKET_USERNAME_API_ACCESS))
            .getHarnessSecretIdentifier());
    assertEquals(
        Constants.BITBUCKET_USERNAME_API_ACCESS, response.get(Constants.BITBUCKET_USERNAME_API_ACCESS).getEnvName());
    mockedStaticConfigManagerUtils.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateOrUpdateIntegrationConfigForValidUserNameRefAndHasApiAccess() {
    ConnectorInfoDTO connectorInfoDTO = getBitbucketConnectorDTO(true, true, true, true).getConnectorInfo();
    MockedStatic<ConfigManagerUtils> mockRestUtilsConfigManagerUtils = mockStatic(ConfigManagerUtils.class);
    bitbucketConnectorProcessor.createOrUpdateIntegrationConfig(ACCOUNT_IDENTIFIER, connectorInfoDTO);
    verify(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    mockRestUtilsConfigManagerUtils.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInValidUserNameRefAndHasApiAccess() {
    ConnectorDTO connectorDTO = getBitbucketConnectorDTO(true, true, false, true);
    MockedStatic<ConfigManagerUtils> mockedStaticConfigManagerUtils = Mockito.mockStatic(ConfigManagerUtils.class);
    mockedStaticConfigManagerUtils.when(() -> ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any()))
        .thenReturn("");
    Map<String, BackstageEnvVariable> response =
        bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
    assertEquals(PWD_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.BITBUCKET_API_ACCESS_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.BITBUCKET_API_ACCESS_TOKEN, response.get(Constants.BITBUCKET_API_ACCESS_TOKEN).getEnvName());
    assertEquals(
        USER_NAME, ((BackstageEnvConfigVariable) response.get(Constants.BITBUCKET_USERNAME_API_ACCESS)).getValue());
    assertEquals(
        Constants.BITBUCKET_USERNAME_API_ACCESS, response.get(Constants.BITBUCKET_USERNAME_API_ACCESS).getEnvName());
    mockedStaticConfigManagerUtils.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetInfraConnectorType() {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    BitbucketConnectorDTO bitbucketConnectorDTO = new BitbucketConnectorDTO();
    bitbucketConnectorDTO.setExecuteOnDelegate(true);
    connectorInfoDTO.setConnectorConfig(bitbucketConnectorDTO);
    String result = bitbucketConnectorProcessor.getInfraConnectorType(connectorInfoDTO);
    assertEquals(CATALOG_INFRA_CONNECTOR_TYPE_PROXY, result);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetLocationTarget() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    String expectResult =
        "https://bitbucket.dev.harness.io/projects/idp/repos/idp-auto/raw/harness29705?at=refs/heads/master";
    assertEquals(expectResult, bitbucketConnectorProcessor.getLocationTarget(catalogConnectorInfo, "/harness29705"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPerformPushOperation() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    ConnectorDTO connectorDTO = getBitbucketConnectorDTO(true, true, false, false);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(connectorDTO));
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder()
                                                    .decryptedValue(DECRYPTED_SECRET_VALUE)
                                                    .identifier(PWD_SECRET_IDENTIFIER)
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(null)
                                                    .projectIdentifier(null)
                                                    .build();
    when(ngSecretService.getDecryptedSecretValue(any(), any(), any(), any())).thenReturn(decryptedSecretValue);
    MockedStatic<SourcePrincipalContextBuilder> mockedStaticContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    mockedStaticContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(new UserPrincipal(USER_NAME, EMAIL, USER_NAME, ACCOUNT_IDENTIFIER));
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    when(ngSecretServiceNonPrivileged.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().build()));
    ListRemoteResult listRemoteResult = ListRemoteResult.builder().remoteList(Map.of("HEAD", "develop")).build();
    when(gitClientV2.listRemote(any())).thenReturn(listRemoteResult);
    MockedStatic<Files> mockedStaticFile = Mockito.mockStatic(Files.class);
    mockedStaticFile.when(() -> Files.readString(any())).thenReturn("sample content");
    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder().setStatusCode(200).build());
    MockedStatic<GitSyncGrpcClientUtils> mockedStaticGitSyncUtils = mockStatic(GitSyncGrpcClientUtils.class);
    mockedStaticGitSyncUtils.when(() -> GitSyncGrpcClientUtils.retryAndProcessException(any(), any()))
        .thenReturn(CreateFileResponse.newBuilder().setStatusCode(200).build());

    bitbucketConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), true);
    verify(ngSecretService).getDecryptedSecretValue(any(), any(), any(), stringCaptor.capture());
    assertEquals(PWD_SECRET_IDENTIFIER, stringCaptor.getValue());
    mockRestStatic.close();
    mockedStaticContextBuilder.close();
    mockedStaticFile.close();
    mockedStaticGitSyncUtils.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private ConnectorDTO getBitbucketConnectorDTO(
      boolean validType, boolean validIdentifier, boolean validUserNameRef, boolean hasApiAccess) {
    SecretRefData userNameRefData = SecretRefData.builder().identifier(USER_NAME).build();
    SecretRefData secretRefData =
        SecretRefData.builder().identifier(validIdentifier ? PWD_SECRET_IDENTIFIER : "").build();
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder()
            .passwordRef(secretRefData)
            .username(USER_NAME)
            .usernameRef(validUserNameRef ? userNameRefData : null)
            .build();
    BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
        BitbucketHttpCredentialsDTO.builder()
            .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
            .httpCredentialsSpec(bitbucketUsernamePasswordDTO)
            .build();
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO = BitbucketAuthenticationDTO.builder()
                                                                .authType(GitAuthType.HTTP)
                                                                .credentials(bitbucketHttpCredentialsDTO)
                                                                .build();
    BitbucketApiAccessDTO bitbucketApiAccessDTO = BitbucketApiAccessDTO.builder()
                                                      .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                                                      .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                                                .usernameRef(validUserNameRef ? userNameRefData : null)
                                                                .tokenRef(secretRefData)
                                                                .username(USER_NAME)
                                                                .build())
                                                      .build();
    BitbucketConnectorDTO bitbucketConnectorDTO = BitbucketConnectorDTO.builder()
                                                      .apiAccess(hasApiAccess ? bitbucketApiAccessDTO : null)
                                                      .url(URL)
                                                      .connectionType(GitConnectionType.ACCOUNT)
                                                      .authentication(bitbucketAuthenticationDTO)
                                                      .delegateSelectors(null)
                                                      .executeOnDelegate(false)
                                                      .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(bitbucketConnectorDTO)
                                            .connectorType(validType ? ConnectorType.BITBUCKET : ConnectorType.GITLAB)
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
    connectorDetails.setType(ConnectorDetails.TypeEnum.BITBUCKET);
    catalogConnectorInfo.setConnector(connectorDetails);
    catalogConnectorInfo.setRepo("https://bitbucket.dev.harness.io/scm/idp/idp-auto");
    catalogConnectorInfo.setBranch("master");
    catalogConnectorInfo.setPath("/harness29705");
    return catalogConnectorInfo;
  }
}
