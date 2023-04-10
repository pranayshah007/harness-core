/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.S3UrlStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationCreateStackPassThroughData;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.cloudformation.CloudFormationCreateStackNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({StepUtils.class})
@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class CloudformationCreateStackStepTest extends CategoryTest {
  @Mock PipelineRbacHelper pipelineRbacHelper;
  @Mock CDStepHelper cdStepHelper;
  @Mock CloudformationStepHelper cloudformationStepHelper;
  @Mock StepHelper stepHelper;
  @Mock CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock CloudformationConfigDAL cloudformationConfigDAL;
  @Mock private ProvisionerOutputHelper provisionerOutputHelper;

  @InjectMocks private CloudformationCreateStackStep cloudformationCreateStackStep;

  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  private static final String CONNECTOR_REF = "test-connector";
  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Before
  public void setUpMocks() {
    doNothing().when(provisionerOutputHelper).saveProvisionerOutputByStepIdentifier(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithInlineTemplateStore() {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    InlineCloudformationTemplateFileSpec templateFileSpec = new InlineCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField(CONNECTOR_REF)).build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setTemplateBody(ParameterField.createValueField("data"));
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                    .parameters(parametersFileSpecs)
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.Inline)
                                                      .build())
                                    .build());
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    cloudformationCreateStackStep.validateResources(getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithRemoteTemplateStore() {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField(CONNECTOR_REF)).build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setStore(storeConfigWrapper);
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .connectorRef(ParameterField.createValueField("test" + CONNECTOR_REF))
                                    .parameters(parametersFileSpecs)
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.Remote)
                                                      .build())
                                    .build());
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    cloudformationCreateStackStep.validateResources(getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(4);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_REF);
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(3).getEntityRef().getIdentifier()).isEqualTo("test" + CONNECTOR_REF);
    assertThat(entityDetails.get(3).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteCloudformationTask() {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField(CONNECTOR_REF)).build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setStore(storeConfigWrapper);
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .parameters(parametersFileSpecs)
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.Remote)
                                                      .build())
                                    .build());
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("create-d-selector-1");
    parameters.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    CloudformationTaskNGParameters cloudformationTaskNGParameters = CloudformationTaskNGParameters.builder()
                                                                        .accountId("test-account")
                                                                        .taskType(CloudformationTaskType.CREATE_STACK)
                                                                        .awsConnector(AwsConnectorDTO.builder().build())
                                                                        .region("test-region")
                                                                        .encryptedDataDetails(encryptedDataDetails)
                                                                        .stackName("test-stack-name")
                                                                        .build();
    TaskChainResponse taskChainResponse =
        cloudformationCreateStackStep.executeCloudformationTask(getAmbiance(), stepElementParameters,
            cloudformationTaskNGParameters, CloudFormationCreateStackPassThroughData.builder().build());
    assertThat(taskChainResponse).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    CloudformationTaskNGParameters taskParameters =
        (CloudformationTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.CLOUDFORMATION_TASK_NG.name());
    assertThat(taskSelectorsArgumentCaptor.getValue().get(0).getSelector()).isEqualTo("create-d-selector-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContext() throws Exception {
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());

    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    CloudformationConfig cloudformationConfig = CloudformationConfig.builder().build();

    StoreConfigWrapper storeConfigWrapper = StoreConfigWrapper.builder()
                                                .spec(S3UrlStoreConfig.builder()
                                                          .urls(ParameterField.createValueField(Arrays.asList("url1")))
                                                          .region(ParameterField.createValueField("region"))
                                                          .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                                          .build())
                                                .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setStore(storeConfigWrapper);
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .region(ParameterField.createValueField("region"))
                                    .stackName(ParameterField.createValueField("stack-name"))
                                    .parameters(parametersFileSpecs)
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.Remote)
                                                      .build())
                                    .build());
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    CloudFormationCreateStackPassThroughData passThroughData =
        CloudFormationCreateStackPassThroughData.builder().build();

    CloudformationTaskNGResponse cloudformationTaskNGResponse =
        CloudformationTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .cloudFormationCommandNGResponse(
                CloudFormationCreateStackNGResponse.builder().cloudFormationOutputMap(new HashMap<>()).build())
            .unitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(Arrays.asList(
                        UnitProgress.newBuilder().setUnitName("name").setStatus(UnitStatus.FAILURE).build()))
                    .build())
            .build();
    doNothing().when(cloudformationStepHelper).saveCloudFormationInheritOutput(any(), any(), any(), anyBoolean());
    doReturn(cloudformationConfig).when(cloudformationStepHelper).getCloudformationConfig(any(), any(), any());
    StepResponse response = cloudformationCreateStackStep.finalizeExecutionWithSecurityContext(
        getAmbiance(), stepElementParameters, passThroughData, () -> cloudformationTaskNGResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(cloudformationConfigDAL).saveCloudformationConfig(eq(cloudformationConfig));

    // Verify now that if the passthorugh data is StepExceptionPassThroughData the cloudformation task is not executed
    cloudformationCreateStackStep.finalizeExecutionWithSecurityContext(getAmbiance(), stepElementParameters,
        StepExceptionPassThroughData.builder().build(), () -> cloudformationTaskNGResponse);
    verify(cdStepHelper, times(1)).handleStepExceptionFailure(any());
    reset(cloudformationStepHelper);

    cloudformationCreateStackStep.finalizeExecutionWithSecurityContext(getAmbiance(), stepElementParameters,
        passThroughData, () -> { throw new TaskNGDataException(UnitProgressData.builder().build(), null); });

    verify(cloudformationStepHelper, times(1)).getFailureResponse(any(), any());
    reset(cloudformationStepHelper);

    CloudformationTaskNGResponse failedTaskResponse =
        CloudformationTaskNGResponse.builder()
            .unitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(Arrays.asList(
                        UnitProgress.newBuilder().setUnitName("name").setStatus(UnitStatus.FAILURE).build()))
                    .build())
            .errorMessage("error")
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .build();

    cloudformationCreateStackStep.finalizeExecutionWithSecurityContext(
        getAmbiance(), stepElementParameters, passThroughData, () -> failedTaskResponse);
    verify(cloudformationStepHelper, times(1)).getFailureResponse(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextExceptionThrownInStep() throws Exception {
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());

    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    CloudformationConfig cloudformationConfig = CloudformationConfig.builder().build();

    StoreConfigWrapper storeConfigWrapper = StoreConfigWrapper.builder()
                                                .spec(S3UrlStoreConfig.builder()
                                                          .urls(ParameterField.createValueField(Arrays.asList("url1")))
                                                          .region(ParameterField.createValueField("region"))
                                                          .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                                          .build())
                                                .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setStore(storeConfigWrapper);
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .region(ParameterField.createValueField("region"))
                                    .stackName(ParameterField.createValueField("stack-name"))
                                    .parameters(parametersFileSpecs)
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.Remote)
                                                      .build())
                                    .build());
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    CloudFormationCreateStackPassThroughData passThroughData =
        CloudFormationCreateStackPassThroughData.builder().build();

    CloudformationTaskNGResponse cloudformationTaskNGResponse =
        CloudformationTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .cloudFormationCommandNGResponse(
                CloudFormationCreateStackNGResponse.builder().cloudFormationOutputMap(new HashMap<>()).build())
            .unitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(Arrays.asList(
                        UnitProgress.newBuilder().setUnitName("name").setStatus(UnitStatus.FAILURE).build()))
                    .build())
            .build();
    doThrow(new RuntimeException("testException"))
        .when(cloudformationStepHelper)
        .saveCloudFormationInheritOutput(any(), any(), any(), anyBoolean());
    doReturn(cloudformationConfig).when(cloudformationStepHelper).getCloudformationConfig(any(), any(), any());

    cloudformationCreateStackStep.finalizeExecutionWithSecurityContext(
        getAmbiance(), stepElementParameters, passThroughData, () -> cloudformationTaskNGResponse);

    verify(cloudformationStepHelper, times(1))
        .getFailureResponse(any(), eq("Exception while executing Cloudformation Create Stack step: testException"));
  }
}
