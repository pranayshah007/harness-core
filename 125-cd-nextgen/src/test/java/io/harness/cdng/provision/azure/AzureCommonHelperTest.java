/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

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
    ParameterField<List<String>> delegateSelector = ParameterField.<List<String>>builder().build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().delegateSelectors(delegateSelector).build();
    AzureCreateARMResourcePassThroughData azureCreateARMResourcePassThroughData =
        AzureCreateARMResourcePassThroughData.builder().build();
    Mockito.mockStatic(StepUtils.class);
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any())).thenReturn(taskRequest);
    TaskChainResponse result = azureCommonHelper.getGitFetchFileTaskChainResponse(
        azureHelperTest.getAmbiance(), files, stepElementParameters, azureCreateARMResourcePassThroughData);
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
}
