/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.ssh.AwsSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AwsWinrmInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureWinrmInfraDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.shellscript.HostsOutput;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;
import io.harness.steps.shellscript.WinRmInfraDelegateConfigOutput;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class InfrastructureTaskExecutableStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock InfrastructureStepHelper infrastructureStepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock OutcomeService outcomeService;
  @Mock CDStepHelper cdStepHelper;
  @Mock StepHelper stepHelper;
  @Mock KryoSerializer kryoSerializer;
  @Mock ThrowingSupplier throwingSupplier;
  @Mock private StageExecutionHelper stageExecutionHelper;

  @InjectMocks private InfrastructureTaskExecutableStep infrastructureStep;

  private final String ACCOUNT_ID = "accountId";
  private final Ambiance ambiance =
      Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

  private final AzureConnectorDTO azureConnectorDTO =
      AzureConnectorDTO.builder().delegateSelectors(ImmutableSet.of("delegate1")).build();
  private final ConnectorInfoDTO azureConnectorInfoDTO =
      ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE).connectorConfig(azureConnectorDTO).build();

  private final ParameterField connectorRef = ParameterField.createValueField("connectorRef");
  private final AzureSshInfraDelegateConfig azureSshInfraDelegateConfig =
      AzureSshInfraDelegateConfig.sshAzureBuilder().azureConnectorDTO(azureConnectorDTO).build();
  private final AzureWinrmInfraDelegateConfig azureWinrmInfraDelegateConfig =
      AzureWinrmInfraDelegateConfig.winrmAzureBuilder().azureConnectorDTO(azureConnectorDTO).build();
  private final Infrastructure azureInfra = SshWinRmAzureInfrastructure.builder()
                                                .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                                .connectorRef(connectorRef)
                                                .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                .tags(ParameterField.createValueField(ImmutableMap.of("Env", "Dev")))
                                                .build();

  private final AwsConnectorDTO awsConnectorDTO =
      AwsConnectorDTO.builder()
          .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
          .delegateSelectors(ImmutableSet.of("delegate1"))
          .build();
  private final ConnectorInfoDTO awsConnectorInfoDTO =
      ConnectorInfoDTO.builder().connectorType(ConnectorType.AWS).connectorConfig(awsConnectorDTO).build();
  private final AwsSshInfraDelegateConfig awsSshInfraDelegateConfig =
      AwsSshInfraDelegateConfig.sshAwsBuilder().awsConnectorDTO(awsConnectorDTO).build();
  private final AwsWinrmInfraDelegateConfig awsWinrmInfraDelegateConfig =
      AwsWinrmInfraDelegateConfig.winrmAwsBuilder().awsConnectorDTO(awsConnectorDTO).build();
  private final Infrastructure awsInfra =
      SshWinRmAwsInfrastructure.builder()
          .connectorRef(connectorRef)
          .credentialsRef(ParameterField.createValueField("sshKeyRef"))
          .region(ParameterField.createValueField("regionId"))
          .awsInstanceFilter(AwsInstanceFilter.builder()
                                 .vpcs(Arrays.asList("vpc1"))
                                 .tags(ParameterField.createValueField(Collections.singletonMap("testTag", "test")))
                                 .build())
          .build();

  @Before
  public void setUp() {
    when(stepHelper.getEnvironmentType(eq(ambiance))).thenReturn(EnvironmentType.ALL);
    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithSshAzureInfra() {
    when(infrastructureStepHelper.validateAndGetConnector(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(azureConnectorInfoDTO);
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);

    TaskRequest taskRequest =
        infrastructureStep.obtainTaskInternal(ambiance, azureInfra, StepInputPackage.builder().build());

    ArgumentCaptor<SshInfraDelegateConfigOutput> azureConfigOutputCaptor =
        ArgumentCaptor.forClass(SshInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            azureConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    SshInfraDelegateConfigOutput azureInfraDelegateConfigOutput = azureConfigOutputCaptor.getValue();
    assertThat(azureInfraDelegateConfigOutput).isNotNull();
    assertThat(azureInfraDelegateConfigOutput.getSshInfraDelegateConfig()).isEqualTo(azureSshInfraDelegateConfig);

    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.NG_AZURE_TASK.name());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithSshAwsInfra() {
    when(infrastructureStepHelper.validateAndGetConnector(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(awsConnectorInfoDTO);
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);

    TaskRequest taskRequest =
        infrastructureStep.obtainTaskInternal(ambiance, awsInfra, StepInputPackage.builder().build());

    ArgumentCaptor<SshInfraDelegateConfigOutput> awsConfigOutputCaptor =
        ArgumentCaptor.forClass(SshInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            awsConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    SshInfraDelegateConfigOutput awsInfraDelegateConfigOutput = awsConfigOutputCaptor.getValue();
    assertThat(awsInfraDelegateConfigOutput).isNotNull();
    assertThat(awsInfraDelegateConfigOutput.getSshInfraDelegateConfig()).isEqualTo(awsSshInfraDelegateConfig);

    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.NG_AWS_TASK.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithWinRmAzureInfra() {
    when(infrastructureStepHelper.validateAndGetConnector(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(azureConnectorInfoDTO);
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.WINRM).build());
    when(cdStepHelper.getWinRmInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureWinrmInfraDelegateConfig);

    TaskRequest taskRequest =
        infrastructureStep.obtainTaskInternal(ambiance, azureInfra, StepInputPackage.builder().build());

    ArgumentCaptor<WinRmInfraDelegateConfigOutput> azureConfigOutputCaptor =
        ArgumentCaptor.forClass(WinRmInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            azureConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    WinRmInfraDelegateConfigOutput azureInfraDelegateConfigOutput = azureConfigOutputCaptor.getValue();
    assertThat(azureInfraDelegateConfigOutput).isNotNull();
    assertThat(azureInfraDelegateConfigOutput.getWinRmInfraDelegateConfig()).isEqualTo(azureWinrmInfraDelegateConfig);

    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.NG_AZURE_TASK.name());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithWinRmAwsInfra() {
    when(infrastructureStepHelper.validateAndGetConnector(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(awsConnectorInfoDTO);
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.WINRM).build());
    when(cdStepHelper.getWinRmInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsWinrmInfraDelegateConfig);

    TaskRequest taskRequest =
        infrastructureStep.obtainTaskInternal(ambiance, awsInfra, StepInputPackage.builder().build());

    ArgumentCaptor<WinRmInfraDelegateConfigOutput> awsConfigOutputCaptor =
        ArgumentCaptor.forClass(WinRmInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            awsConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    WinRmInfraDelegateConfigOutput awsInfraDelegateConfigOutput = awsConfigOutputCaptor.getValue();
    assertThat(awsInfraDelegateConfigOutput).isNotNull();
    assertThat(awsInfraDelegateConfigOutput.getWinRmInfraDelegateConfig()).isEqualTo(awsWinrmInfraDelegateConfig);

    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.NG_AWS_TASK.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleAzureTaskResultWithSecurityContextSuccess() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);
    doNothing()
        .when(stageExecutionHelper)
        .saveStageExecutionInfoAndPublishExecutionInfoKey(
            eq(ambiance), any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AZURE));
    doNothing()
        .when(stageExecutionHelper)
        .addRollbackArtifactToStageOutcomeIfPresent(eq(ambiance), any(StepResponseBuilder.class),
            any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AZURE));
    AzureHostsResponse azureHostsResponse =
        AzureHostsResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .hosts(Arrays.asList(AzureHostResponse.builder().hostName("host1").build(),
                AzureHostResponse.builder().hostName("host2").build()))
            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("azure-hosts-response", azureHostsResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, azureInfra, responseDataSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).isNotEmpty();
    assertThat(response.getFailureInfo()).isNull();
    assertThat(response.getUnitProgressList()).isNotEmpty();

    ArgumentCaptor<HostsOutput> hostsOutputArgumentCaptor = ArgumentCaptor.forClass(HostsOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.OUTPUT), hostsOutputArgumentCaptor.capture(),
            eq(StepCategory.STAGE.name()));

    HostsOutput hostsOutput = hostsOutputArgumentCaptor.getValue();
    assertThat(hostsOutput).isNotNull();
    assertThat(hostsOutput.getHosts()).containsExactly("host1", "host2");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleAwsTaskResultWithSecurityContextSuccess() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);
    doNothing()
        .when(stageExecutionHelper)
        .saveStageExecutionInfoAndPublishExecutionInfoKey(
            eq(ambiance), any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AWS));
    doNothing()
        .when(stageExecutionHelper)
        .addRollbackArtifactToStageOutcomeIfPresent(eq(ambiance), any(StepResponseBuilder.class),
            any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AWS));
    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse =
        AwsListEC2InstancesTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .instances(Arrays.asList(AwsEC2Instance.builder().publicDnsName("host1").build()))
            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("aws-hosts-response", awsListEC2InstancesTaskResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, awsInfra, responseDataSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).isNotEmpty();
    assertThat(response.getFailureInfo()).isNull();
    assertThat(response.getUnitProgressList()).isNotEmpty();

    ArgumentCaptor<HostsOutput> hostsOutputArgumentCaptor = ArgumentCaptor.forClass(HostsOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.OUTPUT), hostsOutputArgumentCaptor.capture(),
            eq(StepCategory.STAGE.name()));

    HostsOutput hostsOutput = hostsOutputArgumentCaptor.getValue();
    assertThat(hostsOutput).isNotNull();
    assertThat(hostsOutput.getHosts()).containsExactly("host1");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleAzureTaskResultWithSecurityContextFailure() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);

    AzureHostsResponse azureHostsResponse = AzureHostsResponse.builder()
                                                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                .errorSummary("Failed to load hosts from Azure")
                                                .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("azure-hosts-response", azureHostsResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, azureInfra, responseDataSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0)).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo("GENERAL_ERROR");
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("Failed to load hosts from Azure");
    assertThat(response.getUnitProgressList()).isNotEmpty();

    verify(executionSweepingOutputService, times(0)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleAwsTaskResultWithSecurityContextFailure() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);

    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse =
        AwsListEC2InstancesTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    Map<String, ResponseData> responseDataMap = ImmutableMap.of("aws-hosts-response", awsListEC2InstancesTaskResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, awsInfra, responseDataSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0)).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo("GENERAL_ERROR");
    assertThat(response.getUnitProgressList()).isNotEmpty();

    verify(executionSweepingOutputService, times(0)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleAzureTaskResultWithSecurityContextException() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);

    when(throwingSupplier.get()).thenThrow(new InvalidRequestException("Task failed to complete"));

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, azureInfra, throwingSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0)).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo("GENERAL_ERROR");
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getUnitProgressList()).isNotEmpty();

    verify(executionSweepingOutputService, times(0)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleAwsTaskResultWithSecurityContextException() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);

    when(throwingSupplier.get()).thenThrow(new InvalidRequestException("Task failed to complete"));

    StepResponse response =
        infrastructureStep.handleTaskResultWithSecurityContext(ambiance, awsInfra, throwingSupplier);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0)).isNotNull();
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo("GENERAL_ERROR");
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getUnitProgressList()).isNotEmpty();

    verify(executionSweepingOutputService, times(0)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAwsInfrastructure() {
    SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();

    infrastructureStep.validateInfrastructure(builder.build());
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    builder.credentialsRef(credentialsRef).connectorRef(ParameterField.createValueField("value")).build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(any(), eq(credentialsRef), any());

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");

    ParameterField connectorRef2 = new ParameterField<>(null, null, true, "expression2", null, true);
    builder.connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
        .credentialsRef(ParameterField.createValueField("value"))
        .build();
    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(connectorRef2), any(), any());

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructure() {
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    infrastructureStep.validateInfrastructure(infrastructure);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateConnector() {
    ParameterField<String> gcpSaConnectorRef = ParameterField.createValueField("account.gcp-sa");
    ParameterField<String> gcpDelegateConnectorRef = ParameterField.createValueField("account.gcp-delegate");
    ParameterField<String> missingConnectorRef = ParameterField.createValueField("account.missing");

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
    GcpConnectorDTO gcpConnectorServiceAccount =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().build())
                            .build())
            .build();
    GcpConnectorDTO gcpConnectorInheritFromDelegate =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    doThrow(new InvalidRequestException(
                format("Connector not found for identifier : [%s]", missingConnectorRef.getValue())))
        .when(infrastructureStepHelper)
        .validateAndGetConnector(eq(missingConnectorRef), eq(ambiance), any());

    doReturn(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build())
        .when(infrastructureStepHelper)
        .validateAndGetConnector(eq(gcpSaConnectorRef), eq(ambiance), any());

    doReturn(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorInheritFromDelegate).build())
        .when(infrastructureStepHelper)
        .validateAndGetConnector(eq(gcpDelegateConnectorRef), eq(ambiance), any());

    assertConnectorValidationMessage(K8sGcpInfrastructure.builder().connectorRef(missingConnectorRef).build(),
        "Connector not found for identifier : [account.missing]");

    assertThatCode(()
                       -> infrastructureStep.validateConnector(
                           K8sGcpInfrastructure.builder().connectorRef(gcpSaConnectorRef).build(), ambiance))
        .doesNotThrowAnyException();

    assertThatCode(()
                       -> infrastructureStep.validateConnector(
                           K8sGcpInfrastructure.builder().connectorRef(gcpDelegateConnectorRef).build(), ambiance))
        .doesNotThrowAnyException();
  }

  private void assertConnectorValidationMessage(Infrastructure infrastructure, String message) {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance))
        .hasMessageContaining(message);
  }
}
