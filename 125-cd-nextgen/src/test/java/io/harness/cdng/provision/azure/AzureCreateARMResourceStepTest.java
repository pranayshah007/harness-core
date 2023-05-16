/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConstants;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.webapp.AzureWebAppStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepScope.AzureCreateARMResourceStepScopeBuilder;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskParameters;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
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
  private static final String AZURE_TEMPLATE_SELECTOR = "Azure ARM Template File";
  private static final String AZURE_PARAMETER_SELECTOR = "Azure ARM Parameter File";
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private AzureWebAppStepHelper azureWebAppStepHelper;
  @Mock private AzureARMConfigDAL azureARMConfigDAL;
  @Mock private AzureCommonHelper azureCommonHelper;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private ProvisionerOutputHelper provisionerOutputHelper;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  private AzureTestHelper azureHelperTest = new AzureTestHelper();

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
    doNothing().when(provisionerOutputHelper).saveProvisionerOutputByStepIdentifier(any(), any());
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

    stepParameters.setProvisionerIdentifier(ParameterField.createValueField("foobar"));
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
  public void testValidateResourcesWithAllHarnessStore() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    stepParameters.setProvisionerIdentifier(ParameterField.createValueField("foobar"));
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test"))).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test2"))).build())
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
    assertThat(entityDetails.size()).isEqualTo(1);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("azure");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithAllHarnessStoreAndMoreThanOneFileSelected() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    stepParameters.setProvisionerIdentifier(ParameterField.createValueField("foobar"));
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(Arrays.asList("project:/throne"))).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder()
                      .files(ParameterField.createValueField(Arrays.asList("project:/throne", "project:/haunt")))
                      .build())
            .build();
    parameterFileBuilder.setStore(fileStoreConfigWrapper);
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfigurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder()
                                                  .templateFile(templateFileBuilder)
                                                  .parameters(parameterFileBuilder)
                                                  .connectorRef(ParameterField.createValueField("azure"))
                                                  .build());
    StepElementParameters step = StepElementParameters.builder().spec(stepParameters).build();
    try {
      azureCreateStep.validateResources(azureHelperTest.getAmbiance(), step);
    } catch (InvalidArgumentsException ex) {
      assertThat(ex).isNotNull();
      assertThat(ex.getMessage())
          .isEqualTo("The Harness store configuration should be pointing to a single template file");
    }
    verify(pipelineRbacHelper, times(0))
        .checkRuntimePermissions(eq(azureHelperTest.getAmbiance()), captor.capture(), eq(true));
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

    stepParameters.setConfigurationParameters(
        AzureCreateARMResourceStepConfigurationParameters.builder()
            .templateFile(templateFileBuilder)
            .parameters(parameterFileBuilder)
            .connectorRef(ParameterField.createValueField("azure"))
            .scope(AzureCreateARMResourceStepScope.builder()
                       .type(AzureScopeTypesNames.ManagementGroup)
                       .spec(AzureManagementSpec.builder()
                                 .location(ParameterField.<String>builder().value("abc").build())
                                 .managementGroupId(ParameterField.<String>builder().value("cde").build())
                                 .build())
                       .build())
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

    verify(azureARMConfigDAL).saveAzureARMConfig(any());
    verify(azureCommonHelper, times(1))
        .getGitFetchFileTaskChainResponse(eq(azureHelperTest.getAmbiance()), eq(fetchFiles), eq(step), eq(passData),
            eq(Arrays.asList(K8sCommandUnitConstants.FetchFiles, AzureConstants.EXECUTE_ARM_DEPLOYMENT,
                AzureConstants.ARM_DEPLOYMENT_STEADY_STATE, AzureConstants.ARM_DEPLOYMENT_OUTPUTS)),
            eq(null));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteAzurePreDeploymentDataShouldBeFetched() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper parametersStore =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/throne"))).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/York"))).build())
            .build();
    parameterFileBuilder.setStore(parametersStore);
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfigurationParameters(AzureCreateARMResourceStepConfigurationParameters.builder()
                                                  .templateFile(templateFileBuilder)
                                                  .parameters(parameterFileBuilder)
                                                  .connectorRef(ParameterField.createValueField("azure"))
                                                  .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);
    AppSettingsFile templateFile = AppSettingsFile.create("foobar");
    AppSettingsFile parametersFile = AppSettingsFile.create("barbar");
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_TEMPLATE_SELECTOR, (HarnessStore) templateStore.getSpec()))
        .thenReturn(templateFile);
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_PARAMETER_SELECTOR, (HarnessStore) parametersStore.getSpec()))
        .thenReturn(parametersFile);
    Mockito.mockStatic(TaskRequestsUtils.class);
    Mockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    TaskChainResponse taskChainResponse = azureCreateStep.startChainLinkAfterRbac(
        azureHelperTest.getAmbiance(), createStep("RG", templateStore, parametersStore), inputPackage);

    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureFetchArmPreDeploymentDataTaskParameters taskNGParameters =
        (AzureFetchArmPreDeploymentDataTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("abc");
    assertThat(taskNGParameters.getSubscriptionId()).isEqualTo("cde");
    assertThat(taskChainResponse.isChainEnd()).isFalse();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithNoGitParametersAndTemplates() {
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test"))).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test2"))).build())
            .build();
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep("SUBS", templateStore, fileStoreConfigWrapper);
    when(azureCommonHelper.getParametersGitFetchFileConfigs(any(), any())).thenReturn(null);
    when(azureCommonHelper.isTemplateStoredOnGit(any())).thenReturn(false);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);
    AppSettingsFile templateFile = AppSettingsFile.create("foobar");
    AppSettingsFile parametersFile = AppSettingsFile.create("barbar");
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_TEMPLATE_SELECTOR, (HarnessStore) templateStore.getSpec()))
        .thenReturn(templateFile);
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_PARAMETER_SELECTOR, (HarnessStore) fileStoreConfigWrapper.getSpec()))
        .thenReturn(parametersFile);

    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);

    TaskChainResponse taskChainResponse =
        azureCreateStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);

    assertThat(taskChainResponse).isNotNull();
    verify(azureARMConfigDAL).saveAzureARMConfig(any());
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
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
  public void testExecuteAzureCreateStepTaskWithHarnessParametersAndTemplates() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/throne"))).build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/York"))).build())
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
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);
    AppSettingsFile templateFile = AppSettingsFile.create("foobar");
    AppSettingsFile parametersFile = AppSettingsFile.create("barbar");
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_TEMPLATE_SELECTOR, (HarnessStore) templateStore.getSpec()))
        .thenReturn(templateFile);
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_PARAMETER_SELECTOR, (HarnessStore) fileStoreConfigWrapper.getSpec()))
        .thenReturn(parametersFile);
    Mockito.mockStatic(TaskRequestsUtils.class);
    Mockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    ArgumentCaptor<List<String>> unitsArgumentCaptor = ArgumentCaptor.forClass(List.class);

    TaskChainResponse taskChainResponse = azureCreateStep.startChainLinkAfterRbac(
        azureHelperTest.getAmbiance(), createStep("SUBS", templateStore, fileStoreConfigWrapper), inputPackage);
    assertThat(taskChainResponse).isNotNull();
    verify(azureARMConfigDAL).saveAzureARMConfig(any());
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(),
        unitsArgumentCaptor.capture(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getParametersBody()).isEqualTo(parametersFile);
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo(templateFile);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(unitsArgumentCaptor.getValue())
        .isEqualTo(Arrays.asList(AzureConstants.EXECUTE_ARM_DEPLOYMENT, AzureConstants.ARM_DEPLOYMENT_STEADY_STATE,
            AzureConstants.ARM_DEPLOYMENT_OUTPUTS));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithHarnessParametersAndGitTemplates() {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/throne"))).build())
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
    stepParameters.setConfigurationParameters(
        AzureCreateARMResourceStepConfigurationParameters.builder()
            .templateFile(templateFileBuilder)
            .parameters(parameterFileBuilder)
            .connectorRef(ParameterField.createValueField("azure"))
            .scope(AzureCreateARMResourceStepScope.builder()
                       .type(AzureScopeTypesNames.ManagementGroup)
                       .spec(AzureManagementSpec.builder()
                                 .location(ParameterField.<String>builder().value("abc").build())
                                 .managementGroupId(ParameterField.<String>builder().value("cde").build())
                                 .build())
                       .build())
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

    AppSettingsFile parametersFile = AppSettingsFile.create("barbar");
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_PARAMETER_SELECTOR, (HarnessStore) fileStoreConfigWrapper.getSpec()))
        .thenReturn(parametersFile);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    azureCreateStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);

    verify(azureARMConfigDAL).saveAzureARMConfig(any());
    verify(azureCommonHelper, times(1))
        .getGitFetchFileTaskChainResponse(eq(azureHelperTest.getAmbiance()), eq(fetchFiles), eq(step), eq(passData),
            eq(Arrays.asList(K8sCommandUnitConstants.FetchFiles, AzureConstants.EXECUTE_ARM_DEPLOYMENT,
                AzureConstants.ARM_DEPLOYMENT_STEADY_STATE, AzureConstants.ARM_DEPLOYMENT_OUTPUTS)),
            eq(null));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextFailing() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
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
    StepElementParameters step = createStep("RG", templateStore, fileStoreConfigWrapper);
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
    StepElementParameters step = createStep("RG", templateStore, fileStoreConfigWrapper);
    GitFetchResponse response = getGitFetchResponse();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(cdExpressionResolver.renderExpression(any(), eq("template"))).thenReturn("template");
    when(cdExpressionResolver.renderExpression(any(), eq("file"))).thenReturn("file");
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody().getFileContent()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody().getFileContent()).isEqualTo("file");
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
    verify(cdExpressionResolver, times(2)).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextResourceGroupAndTemplateHarnessStore() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("parameters-connector-ref"))
                      .build())
            .build();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test2"))).build())
            .build();
    StepElementParameters step = createStep("RG", templateStore, fileStoreConfigWrapper);
    GitFetchResponse response = getGitFetchResponseForParametersOnly();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(cdExpressionResolver.renderExpression(any(), eq("Inline"))).thenReturn("Inline");
    when(cdExpressionResolver.renderExpression(any(), eq("file"))).thenReturn("file");
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(any(), any(), any()))
        .thenReturn(AppSettingsFile.builder().fileContent("Inline").build());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody().getFileContent()).isEqualTo("Inline");
    assertThat(taskNGParameters.getParametersBody().getFileContent()).isEqualTo("file");
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
    verify(cdExpressionResolver, times(2)).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextSubscription() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
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
    StepElementParameters step = createStep("SUBS", templateStore, fileStoreConfigWrapper);
    GitFetchResponse response = getGitFetchResponse();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(cdExpressionResolver.renderExpression(any(), eq("template"))).thenReturn("template");
    when(cdExpressionResolver.renderExpression(any(), eq("file"))).thenReturn("file");
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody().getFileContent()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody().getFileContent()).isEqualTo("file");
    assertThat(taskNGParameters.getDeploymentDataLocation()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
    verify(cdExpressionResolver, times(2)).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextManagement() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
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
    StepElementParameters step = createStep("MNG", templateStore, fileStoreConfigWrapper);
    GitFetchResponse response = getGitFetchResponse();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(cdExpressionResolver.renderExpression(any(), eq("template"))).thenReturn("template");
    when(cdExpressionResolver.renderExpression(any(), eq("file"))).thenReturn("file");
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
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
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody().getFileContent()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody().getFileContent()).isEqualTo("file");
    assertThat(taskNGParameters.getManagementGroupId()).isEqualTo("cde");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
    verify(cdExpressionResolver, times(2)).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithAzureFetchArmPreDeploymentDataTaskResponse() throws Exception {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(
                HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/throne"))).build())
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
    stepParameters.setConfigurationParameters(
        AzureCreateARMResourceStepConfigurationParameters.builder()
            .templateFile(templateFileBuilder)
            .parameters(parameterFileBuilder)
            .connectorRef(ParameterField.createValueField("azure"))
            .scope(AzureCreateARMResourceStepScope.builder()
                       .type(AzureScopeTypesNames.ManagementGroup)
                       .spec(AzureManagementSpec.builder()
                                 .location(ParameterField.<String>builder().value("abc").build())
                                 .managementGroupId(ParameterField.<String>builder().value("cde").build())
                                 .build())
                       .build())
            .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    StepElementParameters step = StepElementParameters.builder().spec(stepParameters).build();
    AzureFetchArmPreDeploymentDataTaskResponse response =
        AzureFetchArmPreDeploymentDataTaskResponse.builder()
            .azureARMPreDeploymentData(AzureARMPreDeploymentData.builder()
                                           .resourceGroup("resourceGroup")
                                           .subscriptionId("subscriptionId")
                                           .resourceGroupTemplateJson("{}")
                                           .build())
            .build();

    ArrayList<GitFetchFilesConfig> fetchFiles = new ArrayList<>(Arrays.asList(azureHelperTest.getARMTemplate()));
    when(azureCommonHelper.getParametersGitFetchFileConfigs(any(), any())).thenReturn(fetchFiles);
    when(azureCommonHelper.isTemplateStoredOnGit(any())).thenReturn(true);
    AzureCreateARMResourcePassThroughData passData = AzureCreateARMResourcePassThroughData.builder().build();
    when(azureCommonHelper.getAzureCreatePassThroughData(any())).thenReturn(passData);
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    AppSettingsFile parametersFile = AppSettingsFile.create("barbar");
    when(azureWebAppStepHelper.fetchFileContentFromHarnessStore(
             azureHelperTest.getAmbiance(), AZURE_PARAMETER_SELECTOR, (HarnessStore) fileStoreConfigWrapper.getSpec()))
        .thenReturn(parametersFile);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(null)
                                              .build();
    doReturn(taskChainResponse)
        .when(azureCommonHelper)
        .getGitFetchFileTaskChainResponse(any(), any(), any(), any(), any(), any());

    TaskChainResponse resultTaskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    verify(azureARMConfigDAL).saveAzureARMConfig(any());
    verify(azureCommonHelper, times(1))
        .getGitFetchFileTaskChainResponse(eq(azureHelperTest.getAmbiance()), eq(fetchFiles), eq(step), eq(passData),
            eq(Arrays.asList(K8sCommandUnitConstants.FetchFiles, AzureConstants.EXECUTE_ARM_DEPLOYMENT,
                AzureConstants.ARM_DEPLOYMENT_STEADY_STATE, AzureConstants.ARM_DEPLOYMENT_OUTPUTS)),
            eq(null));
    assertThat(resultTaskChainResponse).isEqualTo(taskChainResponse);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextTenant() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
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
    StepElementParameters step = createStep("SUBS", templateStore, fileStoreConfigWrapper);
    GitFetchResponse response = getGitFetchResponse();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(cdExpressionResolver.renderExpression(any(), eq("template"))).thenReturn("template");
    when(cdExpressionResolver.renderExpression(any(), eq("file"))).thenReturn("file");
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getTemplateBody().getFileContent()).isEqualTo("template");
    assertThat(taskNGParameters.getParametersBody().getFileContent()).isEqualTo("file");
    assertThat(taskNGParameters.getDeploymentDataLocation()).isEqualTo("abc");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.ARM_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
    verify(cdExpressionResolver, times(2)).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithException() throws Exception {
    StepExceptionPassThroughData exception =
        StepExceptionPassThroughData.builder()
            .errorMessage("error_msg")
            .unitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(asList(
                        UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.FAILURE).build()))
                    .build())
            .build();
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
    azureCreateStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(),
        createStep("RG", templateStore, fileStoreConfigWrapper), exception,
        () -> getTaskNGResponse(CommandExecutionStatus.FAILURE, UnitStatus.FAILURE, ""));
    verify(cdStepHelper, times(1)).handleStepExceptionFailure(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithFailureStep() throws Exception {
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
    AzureCreateARMResourcePassThroughData passThroughData = AzureCreateARMResourcePassThroughData.builder().build();
    azureCreateStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(),
        createStep("RG", templateStore, fileStoreConfigWrapper), passThroughData,
        () -> getTaskNGResponse(CommandExecutionStatus.FAILURE, UnitStatus.SUCCESS, "foobar"));
    verify(azureCommonHelper, times(1)).getFailureResponse(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSucceededStep() throws Exception {
    AzureCreateARMResourcePassThroughData passThroughData = AzureCreateARMResourcePassThroughData.builder().build();
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
    StepResponse response = azureCreateStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(),
        createStep("RG", templateStore, fileStoreConfigWrapper), passThroughData,
        () -> getTaskNGResponse(CommandExecutionStatus.SUCCESS, UnitStatus.SUCCESS, ""));

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(response.getStepOutcomes().size()).isEqualTo(1);
    assertThat(outcome.getOutcome()).isInstanceOf(AzureCreateARMResourceOutcome.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSucceededThrowsException() {
    AzureCreateARMResourcePassThroughData passThroughData = AzureCreateARMResourcePassThroughData.builder().build();
    ArgumentCaptor<AzureARMConfig> taskDataArgumentCaptor = ArgumentCaptor.forClass(AzureARMConfig.class);
    doNothing().when(azureARMConfigDAL).saveAzureARMConfig(taskDataArgumentCaptor.capture());
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
    InvalidArgumentsException exception = new InvalidArgumentsException("Foobar");
    assertThatThrownBy(()
                           -> azureCreateStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(),
                               createStep("RG", templateStore, fileStoreConfigWrapper), passThroughData,

                               () -> { throw new TaskNGDataException(UnitProgressData.builder().build(), exception); }))
        .isInstanceOf(TaskNGDataException.class);
  }

  private StepElementParameters createStep(
      String type, StoreConfigWrapper templateStore, StoreConfigWrapper fileStore) {
    AzureCreateARMResourceStepParameters stepParameters = new AzureCreateARMResourceStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();

    parameterFileBuilder.setStore(fileStore);
    templateFileBuilder.setStore(templateStore);
    AzureCreateARMResourceStepScopeBuilder builder = AzureCreateARMResourceStepScope.builder();
    switch (type) {
      case "RG":
        AzureResourceGroupSpec scopeR = AzureResourceGroupSpec.builder()
                                            .resourceGroup(ParameterField.<String>builder().value("abc").build())
                                            .subscription(ParameterField.<String>builder().value("cde").build())
                                            .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                            .build();

        builder.type(AzureScopeTypesNames.ResourceGroup).spec(scopeR);
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

  private GitFetchResponse getGitFetchResponse() {
    return GitFetchResponse.builder()
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
  }

  private GitFetchResponse getGitFetchResponseForParametersOnly() {
    return GitFetchResponse.builder()
        .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
          {
            put("parameterFile",
                FetchFilesResult.builder()
                    .files(new ArrayList<>(Arrays.asList(GitFile.builder().fileContent("file").build())))
                    .build());
          }
        })
        .build();
  }

  private AzureARMTaskNGResponse getTaskNGResponse(
      CommandExecutionStatus status, UnitStatus unitStatus, String errorMsg) {
    return AzureARMTaskNGResponse.builder()
        .errorMsg(errorMsg)
        .outputs("{\n"
            + "    \"nameResult\": {\n"
            + "      \"type\": \"string\",\n"
            + "      \"value\": \"[variables('user')['user-name']]\"\n"
            + "    }\n"
            + "  }")
        .commandExecutionStatus(status)
        .unitProgressData(UnitProgressData.builder()
                              .unitProgresses(asList(
                                  UnitProgress.newBuilder().setUnitName("Azure Stuff").setStatus(unitStatus).build()))
                              .build())
        .build();
  }
}
