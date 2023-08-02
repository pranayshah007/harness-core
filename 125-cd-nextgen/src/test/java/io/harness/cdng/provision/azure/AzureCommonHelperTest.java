/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureCommonHelperTest extends CategoryTest {
  @Mock private CDStepHelper cdStepHelper;

  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  @Mock private StepHelper stepHelper;
  AzureTestHelper azureHelperTest = new AzureTestHelper();
  @InjectMocks private AzureCommonHelper azureCommonHelper;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
    ConnectorInfoDTO connectorInfoDTO = azureHelperTest.createAzureConnectorDTO();

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("azure", azureHelperTest.getAmbiance());
    ConnectorInfoDTO gitConnectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build())
            .build();
    doReturn(gitConnectorInfoDTO).when(cdStepHelper).getConnector("git", azureHelperTest.getAmbiance());
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(GitConfigDTO.builder().build()).when(cdStepHelper).getScmConnector(any(), any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetGitStoreDelegateConfig() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("foobar")
                                                                          .passwordRef(null)
                                                                          .usernameRef(null)
                                                                          .build())
                                                 .build())
                                .build())
            .apiAccess(GithubApiAccessDTO.builder().build())
            .url("url")
            .build();
    GithubStore store = GithubStore.builder()
                            .connectorRef(ParameterField.createValueField("template-connector-ref"))
                            .folderPath(ParameterField.createValueField("folderPath"))
                            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name("connectorName")
                                            .connectorType(ConnectorType.GIT)
                                            .connectorConfig(githubConnectorDTO)
                                            .build();
    doNothing().when(cdStepHelper).validateGitStoreConfig(store);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    ArrayList<String> paths = new ArrayList<>(Arrays.asList("Foo"));
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(gitConfigAuthenticationInfoHelper).getEncryptedDataDetails(any(), any(), any());
    GitStoreDelegateConfig response =
        azureCommonHelper.getGitStoreDelegateConfig(store, azureHelperTest.getAmbiance(), paths);
    assertThat(response.getGitConfigDTO().getConnectorType()).isEqualTo(ConnectorType.GIT);
    assertThat(response.getPaths()).isEqualTo(paths);
    assertThat(response.getConnectorName()).isEqualTo("connectorName");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetGitStoreDelegateConfigWithAccountType() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("foobar")
                                                                          .passwordRef(null)
                                                                          .usernameRef(null)
                                                                          .build())
                                                 .build())
                                .build())
            .apiAccess(GithubApiAccessDTO.builder().build())
            .url("url")
            .build();
    GithubStore store = GithubStore.builder()
                            .connectorRef(ParameterField.createValueField("template-connector-ref"))
                            .folderPath(ParameterField.createValueField("folderPath"))
                            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name("connectorName")
                                            .connectorType(ConnectorType.GIT)
                                            .connectorConfig(githubConnectorDTO)
                                            .build();
    doNothing().when(cdStepHelper).validateGitStoreConfig(store);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn("url").when(cdStepHelper).getGitRepoUrl(any(), any());
    ArrayList<String> paths = new ArrayList<>(Arrays.asList("Foo"));
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(gitConfigAuthenticationInfoHelper).getEncryptedDataDetails(any(), any(), any());
    GitStoreDelegateConfig response =
        azureCommonHelper.getGitStoreDelegateConfig(store, azureHelperTest.getAmbiance(), paths);
    assertThat(response.getGitConfigDTO().getConnectorType()).isEqualTo(ConnectorType.GIT);
    assertThat(response.getPaths()).isEqualTo(paths);
    assertThat(response.getConnectorName()).isEqualTo("connectorName");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHasGitStoredParametersIsTrueForGitStore() {
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper store = StoreConfigWrapper.builder()
                                   .spec(GithubStore.builder()
                                             .connectorRef(ParameterField.createValueField("template-connector-ref"))
                                             .folderPath(ParameterField.createValueField("folderPath"))
                                             .build())
                                   .build();
    parameterFile.setStore(store);
    AzureCreateARMResourceStepConfigurationParameters parameters =
        AzureCreateARMResourceStepConfigurationParameters.builder().parameters(parameterFile).build();
    assertThat(azureCommonHelper.hasGitStoredParameters(parameters)).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHasGitStoredParametersIsFalseForNoNGitStore() {
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper store = StoreConfigWrapper.builder().spec(HarnessStore.builder().build()).build();
    parameterFile.setStore(store);
    AzureCreateARMResourceStepConfigurationParameters parameters =
        AzureCreateARMResourceStepConfigurationParameters.builder().parameters(parameterFile).build();
    assertThat(azureCommonHelper.hasGitStoredParameters(parameters)).isFalse();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetGitFetchFileTaskChainResponse() {
    List<GitFetchFilesConfig> files = null;
    ParameterField<List<TaskSelectorYaml>> delegateSelector = ParameterField.<List<TaskSelectorYaml>>builder().build();
    AzureCreateARMResourcePassThroughData azureCreateARMResourcePassThroughData =
        AzureCreateARMResourcePassThroughData.builder().build();
    AzureCreateARMResourceStepParameters step =
        AzureCreateARMResourceStepParameters.infoBuilder()
            .configurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder().build())
            .delegateSelectors(delegateSelector)
            .build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(taskRequest);
    StepElementParameters steps = StepElementParameters.builder().spec(step).build();
    TaskChainResponse result = azureCommonHelper.getGitFetchFileTaskChainResponse(azureHelperTest.getAmbiance(), files,
        steps, azureCreateARMResourcePassThroughData, Arrays.asList("test"), null);
    assertThat(result.isChainEnd()).isFalse();
    assertThat(result.getTaskRequest()).isEqualTo(taskRequest);
    assertThat(result.getPassThroughData()).isEqualTo(azureCreateARMResourcePassThroughData);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHasGitStoredTemplateIsFalseForNoNGitStore() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper store = StoreConfigWrapper.builder().spec(HarnessStore.builder().build()).build();
    templateFile.setStore(store);

    assertThat(azureCommonHelper.isTemplateStoredOnGit(templateFile)).isFalse();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHasGitStoredTemplateIsTrueForGitStore() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper store = StoreConfigWrapper.builder()
                                   .spec(GithubStore.builder()
                                             .connectorRef(ParameterField.createValueField("template-connector-ref"))
                                             .folderPath(ParameterField.createValueField("folderPath"))
                                             .build())
                                   .build();
    templateFile.setStore(store);

    assertThat(azureCommonHelper.isTemplateStoredOnGit(templateFile)).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testRetrieveDeploymentMode() {
    ARMScopeType scopeA = ARMScopeType.RESOURCE_GROUP;
    ARMScopeType scopeB = ARMScopeType.SUBSCRIPTION;

    String completeMode = "Complete";
    String IncrementalMode = "Incremental";

    AzureDeploymentMode azureDeploymentMode = azureCommonHelper.retrieveDeploymentMode(scopeA, completeMode);
    assertThat(azureDeploymentMode).isEqualTo(AzureDeploymentMode.COMPLETE);

    AzureDeploymentMode azureDeploymentMode2 = azureCommonHelper.retrieveDeploymentMode(scopeB, completeMode);
    assertThat(azureDeploymentMode2).isEqualTo(AzureDeploymentMode.INCREMENTAL);
    AzureDeploymentMode azureDeploymentMode3 = azureCommonHelper.retrieveDeploymentMode(scopeB, IncrementalMode);
    assertThat(azureDeploymentMode3).isEqualTo(AzureDeploymentMode.INCREMENTAL);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAzureEncryptionsDetails() {
    AzureConnectorDTO connectorDTO =
        AzureConnectorDTO.builder()
            .azureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT)
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                    .config(AzureManualDetailsDTO.builder()
                                .clientId("client-id")
                                .tenantId("tenant-id")
                                .authDTO(AzureAuthDTO.builder()
                                             .azureSecretType(AzureSecretType.SECRET_KEY)
                                             .credentials(AzureClientSecretKeyDTO.builder()
                                                              .secretKey(SecretRefData.builder()
                                                                             .decryptedValue("secret-key".toCharArray())
                                                                             .build())
                                                              .build())
                                             .build())
                                .build())
                    .build())
            .build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Arrays.asList(EncryptedDataDetail.builder().build()));
    List<EncryptedDataDetail> encryptedDataDetails =
        azureCommonHelper.getAzureEncryptionDetails(azureHelperTest.getAmbiance(), connectorDTO);
    assertThat(encryptedDataDetails.size()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetAzureCreatePassThroughDataWithGitFiles() {
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper store = StoreConfigWrapper.builder()
                                   .spec(GithubStore.builder()
                                             .connectorRef(ParameterField.createValueField("template-connector-ref"))
                                             .folderPath(ParameterField.createValueField("folderPath"))
                                             .build())
                                   .build();
    parameterFile.setStore(store);
    AzureCreateARMResourceStepConfigurationParameters parameters =
        AzureCreateARMResourceStepConfigurationParameters.builder().parameters(parameterFile).build();
    AzureCreateARMResourcePassThroughData azureCreateARMResourcePassThroughData =
        azureCommonHelper.getAzureCreatePassThroughData(parameters);
    assertThat(azureCreateARMResourcePassThroughData.hasGitFiles()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetParametersGitFetchFileConfigs() {
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper store =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .paths(ParameterField.<List<String>>builder().value(Arrays.asList("thisIsAPath")).build())
                      .build())
            .build();
    parameterFile.setStore(store);
    AzureCreateARMResourceStepConfigurationParameters parameters =
        AzureCreateARMResourceStepConfigurationParameters.builder().parameters(parameterFile).build();
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), any(), any(), any());
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("foobar")
                                                                          .passwordRef(null)
                                                                          .usernameRef(null)
                                                                          .build())
                                                 .build())
                                .build())
            .apiAccess(GithubApiAccessDTO.builder().build())
            .url("url")
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name("connectorName")
                                            .connectorType(ConnectorType.GIT)
                                            .connectorConfig(githubConnectorDTO)
                                            .build();
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    List<GitFetchFilesConfig> fetchFilesConfigs =
        azureCommonHelper.getParametersGitFetchFileConfigs(azureHelperTest.getAmbiance(), parameters);
    assertThat(fetchFilesConfigs.size()).isEqualTo(1);
    assertThat(fetchFilesConfigs.get(0).getManifestType()).isEqualTo("Azure Parameter");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetGitStoreDelegateConfigForGithubApp() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .authentication(
                GithubAuthenticationDTO.builder()
                    .authType(GitAuthType.HTTP)
                    .credentials(GithubHttpCredentialsDTO.builder()
                                     .type(GithubHttpAuthenticationType.GITHUB_APP)
                                     .httpCredentialsSpec(GithubAppDTO.builder()
                                                              .installationId("id")
                                                              .applicationId("app")
                                                              .privateKeyRef(SecretRefData.builder().build())
                                                              .build())
                                     .build())
                    .build())
            .build();
    GithubStore store = GithubStore.builder()
                            .connectorRef(ParameterField.createValueField("template-connector-ref"))
                            .folderPath(ParameterField.createValueField("folderPath"))
                            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name("connectorName")
                                            .connectorType(ConnectorType.GIT)
                                            .connectorConfig(githubConnectorDTO)
                                            .build();
    doReturn(githubConnectorDTO).when(cdStepHelper).getScmConnector(any(), any(), any());
    doNothing().when(cdStepHelper).validateGitStoreConfig(store);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    ArrayList<String> paths = new ArrayList<>(Arrays.asList("Foo"));
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(gitConfigAuthenticationInfoHelper).getEncryptedDataDetails(any(), any(), any());
    GitStoreDelegateConfig response =
        azureCommonHelper.getGitStoreDelegateConfig(store, azureHelperTest.getAmbiance(), paths);
    assertThat(response.getGitConfigDTO().getConnectorType()).isEqualTo(ConnectorType.GITHUB);
    assertThat(response.getPaths()).isEqualTo(paths);
    assertThat(response.getConnectorName()).isEqualTo("connectorName");
    assertThat(response.getGitConfigDTO()).isInstanceOf(GithubConnectorDTO.class);
  }
}
