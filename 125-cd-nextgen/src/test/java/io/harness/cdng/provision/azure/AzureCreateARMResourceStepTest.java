/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepScope.AzureCreateARMResourceStepScopeBuilder;
import io.harness.cdng.provision.azure.AzureResourceGroupSpec.AzureResourceGroupSpecBuilder;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateARMResourceStepTest extends CategoryTest {
  @Mock PipelineRbacHelper pipelineRbacHelper;
  @Mock CDStepHelper cdStepHelper;
  @Mock StepHelper stepHelper;
  AzureTestHelper azureHelperTest = new AzureTestHelper();
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private SecretManagerClientService secretManagerClientService;

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Mock private AzureCommonHelper azureCommonHelper;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @InjectMocks private AzureCreateARMResourceStep azureCreateStep;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
    ConnectorInfoDTO connectorInfoDTO = azureHelperTest.createAzureConnectorDTO();

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("azure", azureHelperTest.getAmbiance());
    ConnectorInfoDTO gitConnectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build())
            .build();
    doReturn(gitConnectorInfoDTO).when(cdStepHelper).getConnector("github", azureHelperTest.getAmbiance());
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithAllRemote() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("github")).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("github")).build())
            .build();
    parameterFileBuilder.setStore(fileStoreConfigWrapper);
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfigurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder()
                                                  .templateFile(templateFileBuilder)
                                                  .parameters(parameterFileBuilder)
                                                  .connectorRef(ParameterField.createValueField("azure"))
                                                  .build());
    StepElementParameters step = StepElementParameters.builder().spec(stepParameters).build();
    azureCreateStep.validateResources(azureHelperTest.getAmbiance(), step);
    verify(pipelineRbacHelper, times(1))
        .checkRuntimePermissions(eq(azureHelperTest.getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("azure");
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("github");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("github");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithGitParametersAndTemplates() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("parameters-connector-ref"))
                      .build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    parameterFileBuilder.setStore(fileStoreConfigWrapper);
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfigurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder()
                                                  .templateFile(templateFileBuilder)
                                                  .parameters(parameterFileBuilder)
                                                  .connectorRef(ParameterField.createValueField("azure"))
                                                  .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    StepElementParameters step = StepElementParameters.builder().spec(stepParameters).build();

    ArrayList<GitFetchFilesConfig> fetchFiles = new ArrayList<>(Arrays.asList(azureHelperTest.getARMTemplate()));
    when(azureCommonHelper.getParametersGitFetchFileConfigs(any(), any())).thenReturn(fetchFiles);
    when(azureCommonHelper.isTemplateStoredOnGit(any())).thenReturn(true);
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    azureCreateStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);

    verify(azureCommonHelper, times(1))
        .getGitFetchFileTaskChainResponse(azureHelperTest.getAmbiance(), fetchFiles, step, passData);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithNoGitParametersAndTemplates() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("RG");
    when(azureCommonHelper.getParametersGitFetchFileConfigs(any(), any())).thenReturn(null);
    when(azureCommonHelper.isTemplateStoredOnGit(any())).thenReturn(false);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);

    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);

    TaskChainResponse taskChainResponse =
        azureCreateStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextFailing() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("RG");
    CloudformationTaskNGResponse badResponse = CloudformationTaskNGResponse.builder().build();
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> badResponse);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextResourceGroup() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("RG");
    GitFetchResponse response =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
              {
                put("parameterFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("file").build())))
                        .build());
                put("templateFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("template").build())))
                        .build());
              }
            })
            .build();
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody()).isEqualTo("file");
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextSubscription() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("SUBS");
    GitFetchResponse response =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
              {
                put("parameterFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("file").build())))
                        .build());
                put("templateFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("template").build())))
                        .build());
              }
            })
            .build();
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody()).isEqualTo("file");
    assertThat(taskNGParameters.getDeploymentDataLocation()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextManagement() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("MNG");
    GitFetchResponse response =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
              {
                put("parameterFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("file").build())))
                        .build());
                put("templateFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("template").build())))
                        .build());
              }
            })
            .build();
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody()).isEqualTo("file");
    assertThat(taskNGParameters.getManagementGroupId()).isEqualTo("cde");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextTenant() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("SUBS");
    GitFetchResponse response =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
              {
                put("parameterFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("file").build())))
                        .build());
                put("templateFile",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("template").build())))
                        .build());
              }
            })
            .build();
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody()).isEqualTo("file");
    assertThat(taskNGParameters.getDeploymentDataLocation()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  private StepElementParameters createStep(String type) {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder().spec(HarnessStore.builder().build()).build();
    StoreConfigWrapper templateStore = StoreConfigWrapper.builder().spec(HarnessStore.builder().build()).build();
    parameterFileBuilder.setStore(fileStoreConfigWrapper);
    templateFileBuilder.setStore(templateStore);
    AzureCreateARMResourceStepScopeBuilder builder = AzureCreateARMResourceStepScope.builder();
    switch (type) {
      case "RG":
        AzureResourceGroupSpecBuilder scopeR = AzureResourceGroupSpec.builder()
                                                   .resourceGroup(ParameterField.<String>builder().value("abc").build())
                                                   .subscription(ParameterField.<String>builder().value("cde").build())
                                                   .mode(AzureARMResourceDeploymentMode.COMPLETE);

        builder.type(AzureScopeTypesNames.ResourceGroup).spec(scopeR.build());
        break;
      case "SUBS":
        AzureSubscriptionSpec scopeS = AzureSubscriptionSpec.builder()
                                           .location(ParameterField.<String>builder().value("abc").build())
                                           .subscription(ParameterField.<String>builder().value("cde").build())
                                           .build();
        builder.type(AzureScopeTypesNames.Subscription).spec(scopeS);
        break;
      case "MNG":
        AzureManagementSpec scopeM = AzureManagementSpec.builder()
                                         .location(ParameterField.<String>builder().value("abc").build())
                                         .managementGroupId(ParameterField.<String>builder().value("cde").build())
                                         .build();
        builder.type(AzureScopeTypesNames.ManagementGroup).spec(scopeM);
        break;
      case "TNT":
        AzureTenantSpec scopeT =
            AzureTenantSpec.builder().location(ParameterField.<String>builder().value("abc").build()).build();
        builder.type(AzureScopeTypesNames.Tenant).spec(scopeT);
        break;
      default:
        break;
    }
    stepParameters.setConfigurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder()
                                                  .templateFile(templateFileBuilder)
                                                  .parameters(parameterFileBuilder)
                                                  .connectorRef(ParameterField.createValueField("azure"))
                                                  .scope(builder.build())
                                                  .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    return StepElementParameters.builder().spec(stepParameters).build();
  }
}
