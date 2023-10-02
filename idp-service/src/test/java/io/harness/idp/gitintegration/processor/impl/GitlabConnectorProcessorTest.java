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
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientV2Impl;
import io.harness.git.model.CommitAndPushResult;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

@OwnedBy(HarnessTeam.IDP)
public class GitlabConnectorProcessorTest {
  @InjectMocks GitlabConnectorProcessor gitlabConnectorProcessor;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private GitClientV2Impl gitClientV2;
  @Captor private ArgumentCaptor<String> stringCaptor;
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
    ConnectorDTO connectorDTO = getGitlabConnectorDTO(false, true, true);
    gitlabConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidAuthType() {
    ConnectorDTO connectorDTO = getGitlabConnectorDTO(true, false, true);
    gitlabConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoForInvalidSecretIdentifier() {
    ConnectorDTO connectorDTO = getGitlabConnectorDTO(true, true, false);
    gitlabConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetConnectorSecretsInfoWithConfigUpdate() {
    ConnectorDTO connectorDTO = getGitlabConnectorDTO(true, true, true);
    doNothing().when(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    MockedStatic<ConfigManagerUtils> mockRestUtils = mockStatic(ConfigManagerUtils.class);
    gitlabConnectorProcessor.createOrUpdateIntegrationConfig(ACCOUNT_IDENTIFIER, connectorDTO.getConnectorInfo());
    verify(configManagerService).createOrUpdateAppConfigForGitIntegrations(any(), any(), any(), any());
    mockRestUtils.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetInfraConnectorType() {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    GitlabConnectorDTO gitlabConnectorDTO = new GitlabConnectorDTO();
    gitlabConnectorDTO.setExecuteOnDelegate(true);
    connectorInfoDTO.setConnectorConfig(gitlabConnectorDTO);
    String result = gitlabConnectorProcessor.getInfraConnectorType(connectorInfoDTO);
    assertEquals(CATALOG_INFRA_CONNECTOR_TYPE_PROXY, result);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetLocationTarget() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    String expectResult = "harness-core/blob/develop/.harness-idp-entities";
    assertEquals(
        expectResult, gitlabConnectorProcessor.getLocationTarget(catalogConnectorInfo, "/.harness-idp-entities"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPerformPushOperation() {
    CatalogConnectorInfo catalogConnectorInfo = getCatalogConnectorInfo();
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    ConnectorDTO connectorDTO = getGitlabConnectorDTO(true, true, true);
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
    when(gitClientV2.commitAndPush(any())).thenReturn(CommitAndPushResult.builder().build());
    gitlabConnectorProcessor.performPushOperation(
        ACCOUNT_IDENTIFIER, catalogConnectorInfo, "", List.of("catalog-info.yaml"), false);
    verify(ngSecretService).getDecryptedSecretValue(any(), any(), any(), stringCaptor.capture());
    assertEquals(TOKEN_SECRET_IDENTIFIER, stringCaptor.getValue());

    mockRestStatic.close();
    mockedStaticConfigManagerUtils.close();
    mockedStaticContextBuilder.close();
    mockedStaticFile.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private ConnectorDTO getGitlabConnectorDTO(boolean validType, boolean validAuthType, boolean validIdentifier) {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier(validIdentifier ? TOKEN_SECRET_IDENTIFIER : "").build();
    GitlabUsernameTokenDTO gitlabUsernameTokenDTO =
        GitlabUsernameTokenDTO.builder().tokenRef(secretRefData).username(USER_NAME).usernameRef(null).build();
    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        GitlabHttpCredentialsDTO.builder()
            .type(validAuthType ? GitlabHttpAuthenticationType.USERNAME_AND_TOKEN
                                : GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
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
                                            .connectorType(validType ? ConnectorType.GITLAB : ConnectorType.GITHUB)
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
    connectorDetails.setType(ConnectorDetails.TypeEnum.GITLAB);
    catalogConnectorInfo.setConnector(connectorDetails);
    catalogConnectorInfo.setRepo("harness-core");
    catalogConnectorInfo.setBranch("develop");
    catalogConnectorInfo.setPath("/.harness-idp-entities");
    return catalogConnectorInfo;
  }
}
