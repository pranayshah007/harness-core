/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.delegate.task.cloudformation.CloudformationCommandUnit.CreateStack;
import static io.harness.delegate.task.cloudformation.CloudformationCommandUnit.DeleteStack;
import static io.harness.delegate.task.cloudformation.CloudformationTaskType.CREATE_STACK;
import static io.harness.delegate.task.cloudformation.CloudformationTaskType.DELETE_STACK;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.rule.OwnerRule.TMACARI;

import static com.amazonaws.services.cloudformation.model.StackStatus.CREATE_COMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationInheritOutput;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.cloudformation.CloudFormationCreateStackNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class, ExpressionEvaluatorUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class CloudformationRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String ORG_TEST_ID = "org_test_id";
  private static final String ACCOUNT_TEST_ID = "account_test_id";
  private static final String PROJECT_TEST_ID = "project_test_id";
  private static final String PROVISIONER_IDENTIFIER = "provisionerIdentifier";
  private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/test-role";

  @Mock CloudformationStepHelper cloudformationStepHelper;
  @Mock CloudformationConfigDAL cloudformationConfigDAL;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private CDStepHelper cdStepHelper;
  @InjectMocks private CloudformationRollbackStep cloudformationRollbackStep;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacSkipBecauseNoSuccessfulCreatesStack() {
    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build();
    doReturn(null).when(cloudformationStepHelper).getSavedCloudFormationInheritOutput(any(), any());

    TaskRequest taskRequest = cloudformationRollbackStep.obtainTaskAfterRbac(
        getAmbiance(), stepElementParameters, StepInputPackage.builder().build());
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("No successful Create Stack with provisionerIdentifier: [" + PROVISIONER_IDENTIFIER
            + "] found in this stage. Skipping rollback.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacSkipBecauseNoPreviousSuccessfulProvisioning() {
    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build();

    doReturn(CloudFormationInheritOutput.builder().existingStack(true).build())
        .when(cloudformationStepHelper)
        .getSavedCloudFormationInheritOutput(any(), any());
    doReturn(null).when(cloudformationConfigDAL).getRollbackCloudformationConfig(any(), any());

    TaskRequest taskRequest = cloudformationRollbackStep.obtainTaskAfterRbac(
        getAmbiance(), stepElementParameters, StepInputPackage.builder().build());
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("No successful Provisioning found with provisionerIdentifier: [" + PROVISIONER_IDENTIFIER
            + "]. Skipping rollback.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacCreateStackForRollback() {
    CloudformationRollbackStep spyCloudformationRollbackStep = spy(cloudformationRollbackStep);
    TaskRequest expectedTaskRequest = TaskRequest.newBuilder().build();
    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();
    CloudformationTaskNGParameters createStackCloudformationTaskNGParameters =
        CloudformationTaskNGParameters.builder()
            .awsConnector(AwsConnectorDTO.builder().build())
            .encryptedDataDetails(new ArrayList<>())
            .region("region")
            .stackName("stackName")
            .cfCommandUnit(CreateStack)
            .accountId(ACCOUNT_TEST_ID)
            .taskType(CloudformationTaskType.CREATE_STACK)
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build();

    doReturn(CloudFormationInheritOutput.builder().existingStack(true).build())
        .when(cloudformationStepHelper)
        .getSavedCloudFormationInheritOutput(any(), any());
    doReturn(CloudformationConfig.builder().build())
        .when(cloudformationConfigDAL)
        .getRollbackCloudformationConfig(any(), any());
    doReturn(createStackCloudformationTaskNGParameters)
        .when(spyCloudformationRollbackStep)
        .getCreateStackCloudformationTaskNGParameters(any(), any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(expectedTaskRequest);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = spyCloudformationRollbackStep.obtainTaskAfterRbac(
        getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    TaskData taskData = taskDataArgumentCaptor.getValue();
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CLOUDFORMATION_TASK_NG.name());
    assertThat(taskData.getParameters()[0]).isEqualTo(createStackCloudformationTaskNGParameters);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest).isEqualTo(expectedTaskRequest);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacDeleteStackForRollback() {
    CloudformationRollbackStep spyCloudformationRollbackStep = spy(cloudformationRollbackStep);
    TaskRequest expectedTaskRequest = TaskRequest.newBuilder().build();
    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();
    CloudformationTaskNGParameters deleteStackCloudformationTaskNGParameters =
        CloudformationTaskNGParameters.builder()
            .awsConnector(AwsConnectorDTO.builder().build())
            .encryptedDataDetails(new ArrayList<>())
            .region("region")
            .stackName("stackName")
            .cfCommandUnit(DeleteStack)
            .accountId(ACCOUNT_TEST_ID)
            .taskType(CloudformationTaskType.DELETE_STACK)
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build();

    doReturn(CloudFormationInheritOutput.builder().existingStack(false).build())
        .when(cloudformationStepHelper)
        .getSavedCloudFormationInheritOutput(any(), any());
    doReturn(deleteStackCloudformationTaskNGParameters)
        .when(spyCloudformationRollbackStep)
        .getDeleteStackCloudformationTaskNGParameters(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(expectedTaskRequest);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = spyCloudformationRollbackStep.obtainTaskAfterRbac(
        getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    TaskData taskData = taskDataArgumentCaptor.getValue();
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CLOUDFORMATION_TASK_NG.name());
    assertThat(taskData.getParameters()[0]).isEqualTo(deleteStackCloudformationTaskNGParameters);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest).isEqualTo(expectedTaskRequest);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetCreateStackCloudformationTaskNGParameters() {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).build();
    LinkedHashMap<String, List<String>> parametersFiles = new LinkedHashMap<>();
    parametersFiles.put("parameters", Collections.singletonList("parametersFile"));
    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build();
    CloudformationConfig cloudformationConfig =
        CloudformationConfig.builder()
            .connectorRef("connectorRef")
            .parametersFiles(parametersFiles)
            .parameterOverrides(Collections.singletonMap("parameterKey", "parameterValueOverride"))
            .stackName("stackName")
            .region("region")
            .templateBody("templateBody")
            .templateUrl("templateUrl")
            .roleArn(ROLE_ARN)
            .capabilities(Collections.singletonList("CAPABILITY"))
            .stackStatusesToMarkAsSuccess(Collections.singletonList("CREATE_COMPLETE"))
            .tags("tags")
            .build();

    doReturn(connectorInfoDTO).when(cloudformationStepHelper).getConnectorDTO(any(), any());
    doReturn(Collections.singletonMap("parameterKey", "parameterValue"))
        .when(cloudformationStepHelper)
        .getParametersFromJson(any(), any());
    doReturn(new ArrayList<>()).when(cloudformationStepHelper).getAwsEncryptionDetails(any(), any());
    Mockito.mockStatic(ExpressionEvaluatorUtils.class);
    PowerMockito.when(ExpressionEvaluatorUtils.updateExpressions(any(), any())).thenReturn(null);

    CloudformationTaskNGParameters cloudformationTaskNGParameters =
        cloudformationRollbackStep.getCreateStackCloudformationTaskNGParameters(
            getAmbiance(), stepElementParameters, cloudformationConfig);

    verify(cloudformationStepHelper, times(1)).getConnectorDTO(any(), any());
    verify(cloudformationStepHelper, times(1)).getParametersFromJson(any(), any());
    verify(cloudformationStepHelper, times(1)).getAwsEncryptionDetails(any(), any());
    assertThat(cloudformationTaskNGParameters).isNotNull();
    assertThat(cloudformationTaskNGParameters.getAccountId()).isEqualTo(ACCOUNT_TEST_ID);
    assertThat(cloudformationTaskNGParameters.getTaskType()).isEqualTo(CREATE_STACK);
    assertThat(cloudformationTaskNGParameters.getCfCommandUnit()).isEqualTo(CreateStack);
    assertThat(cloudformationTaskNGParameters.getTemplateBody()).isEqualTo("templateBody");
    assertThat(cloudformationTaskNGParameters.getTemplateUrl()).isEqualTo("templateUrl");
    assertThat(cloudformationTaskNGParameters.getAwsConnector()).isEqualTo(awsConnectorDTO);
    assertThat(cloudformationTaskNGParameters.getEncryptedDataDetails()).isNotNull();
    assertThat(cloudformationTaskNGParameters.getRegion()).isEqualTo("region");
    assertThat(cloudformationTaskNGParameters.getCloudFormationRoleArn()).isEqualTo(ROLE_ARN);
    assertThat(cloudformationTaskNGParameters.getStackName()).isEqualTo("stackName");
    assertThat(cloudformationTaskNGParameters.getParameters().get("parameterKey")).isEqualTo("parameterValueOverride");
    assertThat(cloudformationTaskNGParameters.getCapabilities().get(0)).isEqualTo("CAPABILITY");
    assertThat(cloudformationTaskNGParameters.getTags()).isEqualTo("tags");
    assertThat(cloudformationTaskNGParameters.getStackStatusesToMarkAsSuccess().get(0)).isEqualTo(CREATE_COMPLETE);
    assertThat(cloudformationTaskNGParameters.getTimeoutInMs()).isEqualTo(600000);

    cloudformationConfig.setParameterOverrides(null);
    CloudformationTaskNGParameters cloudformationTaskNGParametersWithoutOverideParams =
        cloudformationRollbackStep.getCreateStackCloudformationTaskNGParameters(
            getAmbiance(), stepElementParameters, cloudformationConfig);

    assertThat(cloudformationTaskNGParametersWithoutOverideParams.getParameters().isEmpty());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getDeleteStackCloudformationTaskNGParameters() {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).build();
    CloudFormationInheritOutput cloudFormationInheritOutput = CloudFormationInheritOutput.builder()
                                                                  .connectorRef("connectorRef")
                                                                  .stackName("stackName")
                                                                  .region("region")
                                                                  .roleArn(ROLE_ARN)
                                                                  .build();
    doReturn(connectorInfoDTO).when(cloudformationStepHelper).getConnectorDTO(any(), any());
    doReturn(new ArrayList<>()).when(cloudformationStepHelper).getAwsEncryptionDetails(any(), any());
    Mockito.mockStatic(ExpressionEvaluatorUtils.class);
    PowerMockito.when(ExpressionEvaluatorUtils.updateExpressions(any(), any())).thenReturn(null);

    CloudformationTaskNGParameters cloudformationTaskNGParameters =
        cloudformationRollbackStep.getDeleteStackCloudformationTaskNGParameters(
            getAmbiance(), cloudFormationInheritOutput);

    verify(cloudformationStepHelper, times(1)).getConnectorDTO(any(), any());
    verify(cloudformationStepHelper, times(1)).getAwsEncryptionDetails(any(), any());
    assertThat(cloudformationTaskNGParameters).isNotNull();
    assertThat(cloudformationTaskNGParameters.getAccountId()).isEqualTo(ACCOUNT_TEST_ID);
    assertThat(cloudformationTaskNGParameters.getTaskType()).isEqualTo(DELETE_STACK);
    assertThat(cloudformationTaskNGParameters.getCfCommandUnit()).isEqualTo(DeleteStack);
    assertThat(cloudformationTaskNGParameters.getAwsConnector()).isEqualTo(awsConnectorDTO);
    assertThat(cloudformationTaskNGParameters.getEncryptedDataDetails()).isNotNull();
    assertThat(cloudformationTaskNGParameters.getRegion()).isEqualTo("region");
    assertThat(cloudformationTaskNGParameters.getCloudFormationRoleArn()).isEqualTo(ROLE_ARN);
    assertThat(cloudformationTaskNGParameters.getStackName()).isEqualTo("stackName");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextTaskNGDataException() throws Exception {
    StepResponse stepResponse = StepResponse.builder().status(FAILED).build();
    doReturn(stepResponse).when(cloudformationStepHelper).getFailureResponse(any(), any());

    StepResponse result = cloudformationRollbackStep.handleTaskResultWithSecurityContext(getAmbiance(),
        StepElementParameters.builder().build(),
        () -> { throw new TaskNGDataException(UnitProgressData.builder().build(), null); });

    verify(cloudformationStepHelper, times(1)).getFailureResponse(any(), any());
    verify(cloudformationConfigDAL, times(0)).clearStoredCloudformationConfig(any(), any());
    assertThat(result).isEqualTo(stepResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextTaskNonSuccessExecutionStatus() throws Exception {
    StepResponse stepResponse = StepResponse.builder().status(FAILED).build();
    doReturn(stepResponse).when(cloudformationStepHelper).getFailureResponse(any(), any());

    StepResponse result = cloudformationRollbackStep.handleTaskResultWithSecurityContext(getAmbiance(),
        StepElementParameters.builder().build(),
        () -> CloudformationTaskNGResponse.builder().unitProgressData(UnitProgressData.builder().build()).build());

    verify(cloudformationStepHelper, times(1)).getFailureResponse(any(), any());
    verify(cloudformationConfigDAL, times(0)).clearStoredCloudformationConfig(any(), any());
    assertThat(result).isEqualTo(stepResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextTask() throws Exception {
    CloudformationTaskNGResponse cloudformationTaskNGResponse =
        CloudformationTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .cloudFormationCommandNGResponse(
                CloudFormationCreateStackNGResponse.builder().cloudFormationOutputMap(new HashMap<>()).build())
            .unitProgressData(UnitProgressData.builder().build())
            .build();

    CloudformationRollbackStepParameters cloudformationRollbackStepParameters =
        CloudformationRollbackStepParameters.infoBuilder()
            .configuration(CloudformationRollbackStepConfiguration.builder()
                               .provisionerIdentifier(ParameterField.createValueField(PROVISIONER_IDENTIFIER))
                               .build())
            .build();

    StepResponse result = cloudformationRollbackStep.handleTaskResultWithSecurityContext(getAmbiance(),
        StepElementParameters.builder().spec(cloudformationRollbackStepParameters).build(),
        () -> cloudformationTaskNGResponse);

    assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(result.getStepOutcomes()).isNotNull();
    verify(cloudformationConfigDAL, times(1)).clearStoredCloudformationConfig(any(), any());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_TEST_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_TEST_ID)
        .putSetupAbstractions("orgIdentifier", ORG_TEST_ID)
        .build();
  }
}
