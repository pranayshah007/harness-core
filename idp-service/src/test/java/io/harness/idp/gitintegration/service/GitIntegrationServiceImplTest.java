/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.service;
import static io.harness.idp.common.Constants.PROXY_ENV_NAME;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
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
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.processor.impl.AzureRepoConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.BitbucketConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GitlabConnectorProcessor;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.idp.gitintegration.utils.delegateselectors.DelegateSelectorsCache;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import java.util.*;
import org.junit.After;
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
  private static final String DELEGATE_SELECTOR1 = "ds1";
  private static final String DELEGATE_SELECTOR2 = "ds2";
  private static final String PROXY_MAP1 = "{\"github.com\":true, \"gitlab.com\": false}";
  private static final String PROXY_MAP2 = "{\"github.com\":false, \"gitlab.com\": false}";
  private static final String TEST_IDENTIFIER = "123";
  private static final String TEST_GITHUB_URL = "https://github.com/SarvJ1/catalogtest/blob/main/PREQA_NG.yaml";
  @InjectMocks GithubConnectorProcessor githubConnectorProcessor;
  @InjectMocks GitlabConnectorProcessor gitlabConnectorProcessor;
  @InjectMocks BitbucketConnectorProcessor bitbucketConnectorProcessor;
  @InjectMocks AzureRepoConnectorProcessor azureRepoConnectorProcessor;
  @InjectMocks GitIntegrationServiceImpl gitIntegrationServiceImpl;
  AutoCloseable openMocks;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private CatalogConnectorRepository catalogConnectorRepository;
  @Mock ConnectorProcessorFactory connectorProcessorFactory;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock ConfigManagerService configManagerService;
  @Mock DelegateSelectorsCache delegateSelectorsCache;

  String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  String USER_NAME = "test-username";
  String URL = "https://test-url";
  String CONNECTOR_NAME = "test-connector-name";
  String DECRYPTED_SECRET_VALUE = "test-decrypted-value";
  String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";
  String PWD_SECRET_IDENTIFIER = "test-secret-identifier1";
  String GITHUB_APP_APPLICATION_ID = "test-github-app-id";

  String GITHUB_APP_PRIVATE_KEY_SECRET_IDENTIFIER = "test-github-private-key-secret-identifier";

  String GITHUB_APP_PRIVATE_KEY_DECRYPTED_VALUE = "test-github-private-key-decrypted-value";

  String GITHUB_APP_INSTALLATION_ID = "test-github-installation-id";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
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

    Map<String, BackstageEnvVariable> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorInfoDTO);

    assertEquals(TOKEN_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.GITHUB_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.GITHUB_TOKEN, response.get(Constants.GITHUB_TOKEN).getEnvName());
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

    Map<String, BackstageEnvVariable> response =
        gitlabConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorInfoDTO);

    assertEquals(TOKEN_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.GITLAB_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.GITLAB_TOKEN, response.get(Constants.GITLAB_TOKEN).getEnvName());
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

    Map<String, BackstageEnvVariable> response =
        bitbucketConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorInfoDTO);

    assertEquals(PWD_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.BITBUCKET_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.BITBUCKET_TOKEN, response.get(Constants.BITBUCKET_TOKEN).getEnvName());
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

    Map<String, BackstageEnvVariable> response =
        azureRepoConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorInfoDTO);

    assertEquals(TOKEN_SECRET_IDENTIFIER,
        ((BackstageEnvSecretVariable) response.get(Constants.AZURE_REPO_TOKEN)).getHarnessSecretIdentifier());
    assertEquals(Constants.AZURE_REPO_TOKEN, response.get(Constants.AZURE_REPO_TOKEN).getEnvName());
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

    Map<String, BackstageEnvVariable> response =
        githubConnectorProcessor.getConnectorAndSecretsInfo(ACCOUNT_IDENTIFIER, connectorInfoDTO);

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
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllConnectorDetails() {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    List<CatalogConnectorEntity> catalogConnectorEntityList = new ArrayList<>();
    catalogConnectorEntityList.add(getGithubConnectorEntity());
    catalogConnectorEntityList.add(getGitlabConnectorEntity(delegateSelectors));
    when(catalogConnectorRepository.findAllByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(catalogConnectorEntityList);
    List<CatalogConnectorEntity> result = gitIntegrationServiceImpl.getAllConnectorDetails(ACCOUNT_IDENTIFIER);
    assertEquals(catalogConnectorEntityList.size(), result.size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindByAccountIdAndProviderType() {
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity();
    when(catalogConnectorRepository.findByAccountIdentifierAndConnectorProviderType(ACCOUNT_IDENTIFIER, "Github"))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    Optional<CatalogConnectorEntity> result =
        gitIntegrationServiceImpl.findByAccountIdAndProviderType(ACCOUNT_IDENTIFIER, "Github");
    assertTrue(result.isPresent());
    assertEquals(catalogConnectorEntity, result.get());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveConnectorDetails() throws Exception {
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier("account.testGitlab");
    connectorDetails.setType(ConnectorDetails.TypeEnum.GITLAB);
    GitlabConnectorProcessor processor = mock(GitlabConnectorProcessor.class);
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.GITLAB)).thenReturn(processor);
    Map<String, BackstageEnvVariable> secrets = new HashMap<>();
    secrets.put(Constants.GITLAB_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(TOKEN_SECRET_IDENTIFIER, Constants.GITLAB_TOKEN));
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO(delegateSelectors);
    when(processor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    when(processor.getConnectorAndSecretsInfo(any(), any())).thenReturn(secrets);
    doNothing().when(backstageEnvVariableService).findAndSync(any());
    MockedStatic<GitIntegrationUtils> gitIntegrationUtilsMockedStatic = Mockito.mockStatic(GitIntegrationUtils.class);
    MockedStatic<ConfigManagerUtils> configManagerUtilsMockedStatic = Mockito.mockStatic(ConfigManagerUtils.class);
    when(GitIntegrationUtils.getHostForConnector(any())).thenReturn("dummyUrl");
    when(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any())).thenReturn("Sample Config");
    when(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(any())).thenReturn("Sample Json Schema");
    when(ConfigManagerUtils.isValidSchema(any(), any())).thenReturn(false);
    when(configManagerService.saveConfigForAccount(any(), any(), any())).thenReturn(new AppConfig());
    when(configManagerService.mergeAndSaveAppConfig(any())).thenReturn(MergedAppConfigEntity.builder().build());
    when(processor.getInfraConnectorType(any())).thenReturn("DIRECT");
    CatalogConnectorEntity catalogConnectorEntity = getGitlabConnectorEntity(delegateSelectors);
    when(catalogConnectorRepository.saveOrUpdate(any())).thenReturn(catalogConnectorEntity);
    CatalogConnectorEntity result =
        gitIntegrationServiceImpl.saveConnectorDetails(ACCOUNT_IDENTIFIER, connectorDetails);
    verify(delegateSelectorsCache).put(eq(ACCOUNT_IDENTIFIER), any(), any());
    assertEquals("testGitlab", result.getConnectorIdentifier());
    assertEquals(delegateSelectors, result.getDelegateSelectors());
    gitIntegrationUtilsMockedStatic.close();
    configManagerUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindDefaultConnectorDetails() {
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity();
    when(catalogConnectorRepository.findLastUpdated(ACCOUNT_IDENTIFIER)).thenReturn(catalogConnectorEntity);
    CatalogConnectorEntity result = gitIntegrationServiceImpl.findDefaultConnectorDetails(ACCOUNT_IDENTIFIER);
    assertEquals(catalogConnectorEntity, result);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateOrUpdateConnectorConfigEnvVariable() {
    BackstageEnvConfigVariable variable = new BackstageEnvConfigVariable();
    variable.type(BackstageEnvVariable.TypeEnum.CONFIG);
    variable.envName(PROXY_ENV_NAME);
    variable.value(PROXY_MAP1);
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(PROXY_ENV_NAME, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(variable));
    gitIntegrationServiceImpl.createOrUpdateConnectorConfigEnvVariable(
        ACCOUNT_IDENTIFIER, ConnectorType.GITHUB, CatalogInfraConnectorType.DIRECT);
    variable.setValue(PROXY_MAP2);
    verify(backstageEnvVariableService).update(variable, ACCOUNT_IDENTIFIER);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private CatalogConnectorEntity getGithubConnectorEntity() {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .connectorIdentifier("testGithub")
        .connectorProviderType(ConnectorType.GITHUB.toString())
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }

  private CatalogConnectorEntity getGitlabConnectorEntity(Set<String> delegateSelectors) {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .connectorIdentifier("testGitlab")
        .connectorProviderType(ConnectorType.GITLAB.toString())
        .delegateSelectors(delegateSelectors)
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }

  private ConnectorInfoDTO getConnectorInfoDTO(Set<String> delegateSelectors) {
    return ConnectorInfoDTO.builder()
        .identifier(TEST_IDENTIFIER)
        .connectorType(ConnectorType.GITHUB)
        .connectorConfig(GithubConnectorDTO.builder().url(TEST_GITHUB_URL).delegateSelectors(delegateSelectors).build())
        .build();
  }
}