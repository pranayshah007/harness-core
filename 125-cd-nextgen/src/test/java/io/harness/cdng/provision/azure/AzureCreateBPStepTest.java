/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.cdng.provision.azure.beans.AzureCreateBPPassThroughData;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureBlueprintTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureBlueprintTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.expression.EngineExpressionService;
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
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateBPStepTest extends CategoryTest {
  @Mock PipelineRbacHelper pipelineRbacHelper;
  @Mock CDStepHelper cdStepHelper;
  @Mock StepHelper stepHelper;
  AzureTestHelper azureHelperTest = new AzureTestHelper();
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private FileReference fileReference;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private FileStoreService fileStoreService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private AzureCommonHelper azureCommonHelper;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @InjectMocks private AzureCreateBPStep azureCreateBPStep;

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
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithAllRemote() {
    StepElementParameters step = createStep();

    azureCreateBPStep.validateResources(azureHelperTest.getAmbiance(), step);
    verify(pipelineRbacHelper, times(1))
        .checkRuntimePermissions(eq(azureHelperTest.getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("azure");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("template-ConnectorDisconnectHandler-ref");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithGitParametersAndTemplates() {
    StepElementParameters step = createStep();
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    azureCreateBPStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);
    verify(azureCommonHelper, times(1)).getGitStoreDelegateConfig(any(), any(), any());
    verify(azureCommonHelper, times(1)).getGitFetchFileTaskChainResponse(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAzureCreateStepTaskWithFilesInHarnessStore() {
    StepElementParameters step = createStepHarnessStore();
    FileReference fileReference = FileReference.builder().build();
    Mockito.mockStatic(FileReference.class);
    when(FileReference.of(any(), any(), any(), any())).thenReturn(fileReference);
    FolderNodeDTO folderNodeDTO = FolderNodeDTO.builder().build();
    FileNodeDTO blueprintNodeDTO =
        FileNodeDTO.builder().content("blueprint").name("blueprint.json").path("blueprint.json").build();
    when(engineExpressionService.renderExpression(azureHelperTest.getAmbiance(), blueprintNodeDTO.getContent()))
        .thenReturn(blueprintNodeDTO.getContent());

    FileNodeDTO assignNodeDTO = FileNodeDTO.builder().content("assign").name("assign.json").path("assign.json").build();
    when(engineExpressionService.renderExpression(azureHelperTest.getAmbiance(), assignNodeDTO.getContent()))
        .thenReturn(assignNodeDTO.getContent());

    FolderNodeDTO artifacts = FolderNodeDTO.builder().build();
    FileNodeDTO artifact1 = FileNodeDTO.builder()
                                .name("artifact1.json")
                                .path("artifacts/artifact1.json")
                                .content("artifact content")
                                .build();
    when(engineExpressionService.renderExpression(azureHelperTest.getAmbiance(), artifact1.getContent()))
        .thenReturn(artifact1.getContent());
    FileNodeDTO artifact2 = FileNodeDTO.builder()
                                .name("artifact2.json")
                                .path("artifacts/artifact2.json")
                                .content("artifact 2 content")
                                .build();
    when(engineExpressionService.renderExpression(azureHelperTest.getAmbiance(), artifact2.getContent()))
        .thenReturn(artifact2.getContent());

    artifacts.addChild(artifact1);
    artifacts.addChild(artifact2);
    folderNodeDTO.addChild(artifacts);
    folderNodeDTO.addChild(blueprintNodeDTO);
    folderNodeDTO.addChild(assignNodeDTO);
    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(folderNodeDTO));

    when(cdExpressionResolver.updateExpressions(any(), any()))
        .thenReturn(
            ((AzureCreateBPStepParameters) step.getSpec()).getConfiguration().getTemplateFile().getStore().getSpec());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    Mockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);

    TaskChainResponse taskChainResponse =
        azureCreateBPStep.startChainLinkAfterRbac(azureHelperTest.getAmbiance(), step, inputPackage);

    verify(azureCommonHelper, times(0)).getGitStoreDelegateConfig(any(), any(), any());
    verify(azureCommonHelper, times(0)).getGitFetchFileTaskChainResponse(any(), any(), any(), any(), any(), any());
    assertThat(taskChainResponse).isNotNull();
    verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureBlueprintTaskNGParameters taskNGParameters =
        (AzureBlueprintTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getArtifacts().size()).isEqualTo(2);
    assertThat(taskNGParameters.getBlueprintJson()).isEqualTo("blueprint");
    assertThat(taskNGParameters.getAssignmentJson()).isEqualTo("assign");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextFailing() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep();
    CloudformationTaskNGResponse badResponse = CloudformationTaskNGResponse.builder().build();
    TaskChainResponse taskChainResponse =
        azureCreateBPStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateARMResourcePassThroughData.builder().build(), () -> badResponse);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextResourceGroup() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters step = createStep();
    GitFetchResponse response =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(new HashMap<String, FetchFilesResult>() {
              {
                put("bluePrint",
                    FetchFilesResult.builder()
                        .files(new ArrayList<>(
                            Arrays.asList(GitFile.builder().filePath("blueprint.json").fileContent("file").build(),
                                GitFile.builder().filePath("assign.json").fileContent("assign").build(),
                                GitFile.builder().filePath("artifacts/foobar").fileContent("artifacts").build())))
                        .build());
              }
            })
            .build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(azureCommonHelper.getAzureConnectorConfig(any(), any()))
        .thenReturn((AzureConnectorDTO) azureHelperTest.createAzureConnectorDTO().getConnectorConfig());
    TaskChainResponse taskChainResponse =
        azureCreateBPStep.executeNextLinkWithSecurityContext(azureHelperTest.getAmbiance(), step, inputPackage,
            AzureCreateBPPassThroughData.builder().build(), () -> response);
    assertThat(taskChainResponse).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureBlueprintTaskNGParameters taskNGParameters =
        (AzureBlueprintTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getBlueprintJson()).isEqualTo("file");
    assertThat(taskNGParameters.getAssignmentJson()).isEqualTo("assign");
    assertThat(taskNGParameters.getArtifacts().size()).isEqualTo(1);
    assertThat(taskNGParameters.getScope()).isEqualTo("Subscription");
    assertThat(taskNGParameters).isNotNull();
    assertThat(taskNGParameters.getAzureARMTaskType()).isEqualTo(AzureARMTaskType.BLUEPRINT_DEPLOYMENT);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.AZURE_NG_ARM.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
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
    azureCreateBPStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(), createStep(), exception,
        () -> getTaskNGResponse(CommandExecutionStatus.FAILURE, UnitStatus.FAILURE, ""));
    verify(cdStepHelper, times(1)).handleStepExceptionFailure(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithFailureStep() throws Exception {
    AzureCreateARMResourcePassThroughData passThroughData = AzureCreateARMResourcePassThroughData.builder().build();
    azureCreateBPStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(), createStep(), passThroughData,
        () -> getTaskNGResponse(CommandExecutionStatus.FAILURE, UnitStatus.SUCCESS, "foobar"));
    verify(azureCommonHelper, times(1)).getFailureResponse(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSucceededStep() throws Exception {
    AzureCreateBPPassThroughData passThroughData = AzureCreateBPPassThroughData.builder().build();
    StepResponse response = azureCreateBPStep.finalizeExecutionWithSecurityContext(azureHelperTest.getAmbiance(),
        createStep(), passThroughData, () -> getTaskNGResponse(CommandExecutionStatus.SUCCESS, UnitStatus.SUCCESS, ""));

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes().size()).isEqualTo(0);
  }

  private StepElementParameters createStep() {
    AzureCreateBPStepParameters stepParameters = new AzureCreateBPStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();

    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-ConnectorDisconnectHandler-ref"))
                      .build())
            .build();
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfiguration(AzureCreateBPStepConfigurationParameters.builder()
                                        .templateFile(templateFileBuilder)
                                        .scope(AzureBPScopes.SUBSCRIPTION)
                                        .assignmentName(ParameterField.<String>builder().value("foobar").build())
                                        .connectorRef(ParameterField.createValueField("azure"))
                                        .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    return StepElementParameters.builder().spec(stepParameters).build();
  }

  private StepElementParameters createStepHarnessStore() {
    AzureCreateBPStepParameters stepParameters = new AzureCreateBPStepParameters();
    AzureTemplateFile templateFileBuilder = new AzureTemplateFile();

    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test"))).build())
            .build();
    templateFileBuilder.setStore(templateStore);

    stepParameters.setConfiguration(AzureCreateBPStepConfigurationParameters.builder()
                                        .templateFile(templateFileBuilder)
                                        .scope(AzureBPScopes.SUBSCRIPTION)
                                        .assignmentName(ParameterField.<String>builder().value("foobar").build())
                                        .connectorRef(ParameterField.createValueField("azure"))
                                        .build());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    stepParameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    return StepElementParameters.builder().spec(stepParameters).build();
  }

  private AzureBlueprintTaskNGResponse getTaskNGResponse(
      CommandExecutionStatus status, UnitStatus unitStatus, String errorMsg) {
    return AzureBlueprintTaskNGResponse.builder()
        .errorMsg(errorMsg)
        .outputs("")
        .commandExecutionStatus(status)
        .unitProgressData(
            UnitProgressData.builder()
                .unitProgresses(
                    asList(UnitProgress.newBuilder().setUnitName("Azure BP Stuff").setStatus(unitStatus).build()))
                .build())
        .build();
  }
}
