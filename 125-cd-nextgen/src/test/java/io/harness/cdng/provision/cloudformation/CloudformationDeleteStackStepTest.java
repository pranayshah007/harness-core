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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationInheritOutput;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class CloudformationDeleteStackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String ORG_TEST_ID = "org_test_id";
  private static final String ACCOUNT_TEST_ID = "account_test_id";
  private static final String PROJECT_TEST_ID = "project_test_id";
  private static final String CONNECTOR_TEST_ID = "connector_test_id";
  private static final String STACK_TEST_ID = "stack_test_id";
  private static final String REGION = "us-east-1";
  private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/test-role";
  @Mock private AwsResourceServiceHelper awsHelper;
  @Mock private CloudformationStepHelper cloudformationStepHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private CloudformationConfigDAL cloudformationConfigDAL;
  @Mock private StepHelper stepHelper;
  @Mock CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private CloudformationDeleteStackStep cloudformationDeleteStackStep;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesInline() {
    Mockito.doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());

    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInlineDeleteStackStep();
    cloudformationDeleteStackStep.validateResources(ambiance, stepElementParameters);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));
    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(1);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo(CONNECTOR_TEST_ID);
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo(ACCOUNT_TEST_ID);
    assertThat(entityDetails.get(0).getEntityRef().getOrgIdentifier()).isEqualTo(ORG_TEST_ID);
    assertThat(entityDetails.get(0).getEntityRef().getProjectIdentifier()).isEqualTo(PROJECT_TEST_ID);
    assertThat(entityDetails.get(0).getType()).isEqualTo(EntityType.CONNECTORS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInlineDeleteStackStep();
    doReturn(ACCOUNT_TEST_ID + "/" + ORG_TEST_ID + "/" + PROJECT_TEST_ID)
        .when(cloudformationStepHelper)
        .generateIdentifier(any(), any());
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .identifier(CONNECTOR_TEST_ID)
                                            .connectorConfig(AwsConnectorDTO.builder().build())
                                            .build();
    doReturn(connectorInfoDTO).when(cloudformationStepHelper).getConnectorDTO(any(), any());
    doReturn(ROLE_ARN).when(cloudformationStepHelper).renderValue(any(), any());
    doReturn(new ArrayList<>()).when(awsHelper).getAwsEncryptionDetails(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    TaskRequest taskRequest =
        cloudformationDeleteStackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    CloudformationTaskNGParameters taskParameters =
        (CloudformationTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(CloudformationTaskType.DELETE_STACK);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInherited() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInheritedDeleteStackStep();
    doReturn(ACCOUNT_TEST_ID + "/" + ORG_TEST_ID + "/" + PROJECT_TEST_ID)
        .when(cloudformationStepHelper)
        .generateIdentifier(any(), any());
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .identifier(CONNECTOR_TEST_ID)
                                            .connectorConfig(AwsConnectorDTO.builder().build())
                                            .build();
    doReturn(connectorInfoDTO).when(cloudformationStepHelper).getConnectorDTO(any(), any());
    doReturn(ROLE_ARN).when(cloudformationStepHelper).renderValue(any(), any());
    doReturn(new ArrayList<>()).when(awsHelper).getAwsEncryptionDetails(any(), any());
    doReturn(CloudFormationInheritOutput.builder()
                 .connectorRef(CONNECTOR_TEST_ID)
                 .region(REGION)
                 .roleArn(ROLE_ARN)
                 .stackName(STACK_TEST_ID)
                 .build())
        .when(cloudformationStepHelper)
        .getSavedCloudFormationInheritOutput(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    TaskRequest taskRequest =
        cloudformationDeleteStackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(cloudformationStepHelper, times(1)).getSavedCloudFormationInheritOutput(any(), any());
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    CloudformationTaskNGParameters taskParameters =
        (CloudformationTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(CloudformationTaskType.DELETE_STACK);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInheritedNoInheritOutputFound() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInheritedDeleteStackStep();
    doReturn(ACCOUNT_TEST_ID + "/" + ORG_TEST_ID + "/" + PROJECT_TEST_ID)
        .when(cloudformationStepHelper)
        .generateIdentifier(any(), any());
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .identifier(CONNECTOR_TEST_ID)
                                            .connectorConfig(AwsConnectorDTO.builder().build())
                                            .build();
    doReturn(connectorInfoDTO).when(cloudformationStepHelper).getConnectorDTO(any(), any());
    doReturn(ROLE_ARN).when(cloudformationStepHelper).renderValue(any(), any());
    doReturn(new ArrayList<>()).when(awsHelper).getAwsEncryptionDetails(any(), any());
    doReturn(null).when(cloudformationStepHelper).getSavedCloudFormationInheritOutput(any(), any());

    assertThatThrownBy(()
                           -> cloudformationDeleteStackStep.obtainTaskAfterRbac(
                               ambiance, stepElementParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Did not find any successfully executed Create Stack step for provisioner identifier:");

    verify(cloudformationStepHelper, times(1)).getSavedCloudFormationInheritOutput(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInvalidConfigurationType() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInheritedDeleteStackStep();
    CloudformationDeleteStackStepParameters cloudformationDeleteStackStepParameters =
        (CloudformationDeleteStackStepParameters) stepElementParameters.getSpec();
    cloudformationDeleteStackStepParameters.getConfiguration().setType("Invalid");

    assertThatThrownBy(()
                           -> cloudformationDeleteStackStep.obtainTaskAfterRbac(
                               ambiance, stepElementParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid configuration type:");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = createInlineDeleteStackStep();
    doReturn(ACCOUNT_TEST_ID + "/" + ORG_TEST_ID + "/" + PROJECT_TEST_ID)
        .when(cloudformationStepHelper)
        .generateIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    List<CommandExecutionStatus> statuses =
        Arrays.asList(CommandExecutionStatus.FAILURE, CommandExecutionStatus.SUCCESS);
    EnumMap<CommandExecutionStatus, Status> statusMap = createStatusMap();

    for (CommandExecutionStatus status : statuses) {
      CloudformationTaskNGResponse cloudformationTaskNGResponse = CloudformationTaskNGResponse.builder()
                                                                      .commandExecutionStatus(status)
                                                                      .unitProgressData(unitProgressData)
                                                                      .build();
      StepResponse stepResponse = cloudformationDeleteStackStep.handleTaskResult(
          ambiance, stepElementParameters, () -> cloudformationTaskNGResponse);
      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(statusMap.get(status));
      assertThat(stepResponse.getStepOutcomes()).isNotNull();
    }
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(cloudformationDeleteStackStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_TEST_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_TEST_ID)
        .putSetupAbstractions("orgIdentifier", ORG_TEST_ID)
        .build();
  }
  private EnumMap<CommandExecutionStatus, Status> createStatusMap() {
    EnumMap<CommandExecutionStatus, Status> statusMap = new EnumMap<>(CommandExecutionStatus.class);
    statusMap.put(CommandExecutionStatus.FAILURE, Status.FAILED);
    statusMap.put(CommandExecutionStatus.SUCCESS, Status.SUCCEEDED);
    return statusMap;
  }

  private StepElementParameters createInlineDeleteStackStep() {
    InlineCloudformationDeleteStackStepConfiguration spec =
        InlineCloudformationDeleteStackStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR_TEST_ID))
            .stackName(ParameterField.createValueField(STACK_TEST_ID))
            .region(ParameterField.createValueField(REGION))
            .roleArn(ParameterField.createValueField(ROLE_ARN))
            .build();
    CloudformationDeleteStackStepConfiguration configuration =
        CloudformationDeleteStackStepConfiguration.builder()
            .spec(spec)
            .type(CloudformationDeleteStackStepConfigurationTypes.Inline)
            .build();

    return StepElementParameters.builder()
        .spec(CloudformationDeleteStackStepParameters.infoBuilder().configuration(configuration).build())
        .build();
  }

  private StepElementParameters createInheritedDeleteStackStep() {
    InheritedCloudformationDeleteStackStepConfiguration spec =
        InheritedCloudformationDeleteStackStepConfiguration.builder()
            .provisionerIdentifier(ParameterField.createValueField("provisionerIdentifier"))
            .build();
    CloudformationDeleteStackStepConfiguration configuration =
        CloudformationDeleteStackStepConfiguration.builder()
            .spec(spec)
            .type(CloudformationDeleteStackStepConfigurationTypes.Inherited)
            .build();

    return StepElementParameters.builder()
        .spec(CloudformationDeleteStackStepParameters.infoBuilder().configuration(configuration).build())
        .build();
  }
}
