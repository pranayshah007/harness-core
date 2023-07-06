/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.impl;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.*;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;
import io.harness.utils.NGFeatureFlagHelperService;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

public class AzureRepoConnectorProcessorTest {
  @InjectMocks AzureRepoConnectorProcessor azureRepoConnectorProcessor;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Captor private ArgumentCaptor<String> stringCaptor;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  AutoCloseable openMocks;
  private static final String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  private static final String USER_NAME = "test-username";
  private static final String EMAIL = "test-email@gmail.com";
  private static final String URL = "https://test-url";
  private static final String CONNECTOR_NAME = "test-connector-name";
  private static final String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  private static final String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidType() {
    ConnectorDTO connectorDTO = getAzureRepoConnectorDTO(false, true);
    azureRepoConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidSecretIdentifier() {
    ConnectorDTO connectorDTO = getAzureRepoConnectorDTO(true, false);
    azureRepoConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetInfraConnectorType() {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    AzureRepoConnectorDTO azureRepoConnectorDTO = new AzureRepoConnectorDTO();
    azureRepoConnectorDTO.setExecuteOnDelegate(true);
    connectorInfoDTO.setConnectorConfig(azureRepoConnectorDTO);
    String result = azureRepoConnectorProcessor.getInfraConnectorType(connectorInfoDTO);
    assertEquals(CATALOG_INFRA_CONNECTOR_TYPE_PROXY, result);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetLocationTarget() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    String expectResult = "harness-core?path=/harness-idp-entities&version=GBdevelop";
    assertEquals(
        expectResult, azureRepoConnectorProcessor.getLocationTarget(catalogConnectorInfo, "/harness-idp-entities"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPerformPushOperation() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    ConnectorDTO connectorDTO = getAzureRepoConnectorDTO(true, true);
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
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(GitCommandExecutionResponse.builder().build());

    azureRepoConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), false);
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
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    ConnectorDTO connectorDTO = getAzureRepoConnectorDTO(true, true);
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
    mockedStaticFile.when(() -> Files.readString(any())).thenThrow(IOException.class);

    azureRepoConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), false);

    mockRestStatic.close();
    mockedStaticConfigManagerUtils.close();
    mockedStaticContextBuilder.close();
    mockedStaticFile.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private ConnectorDTO getAzureRepoConnectorDTO(boolean validType, boolean validIdentifier) {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier(validIdentifier ? TOKEN_SECRET_IDENTIFIER : "").build();
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
                                                      .executeOnDelegate(true)
                                                      .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(azureRepoConnectorDTO)
                                            .connectorType(validType ? ConnectorType.AZURE_REPO : ConnectorType.AZURE)
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
    connectorDetails.setType(ConnectorDetails.TypeEnum.AZUREREPO);
    catalogConnectorInfo.setConnector(connectorDetails);
    catalogConnectorInfo.setRepo("harness-core");
    catalogConnectorInfo.setBranch("develop");
    catalogConnectorInfo.setPath("/harness-idp-entities");
    return catalogConnectorInfo;
  }
}
