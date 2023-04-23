/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.INFRA_TASK_EXECUTABLE_STEP_OUTPUT;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.InfrastructureOutcomeProvider;
import io.harness.cdng.infra.InfrastructureValidator;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.instance.InstanceOutcomeHelper;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.HostsOutput;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.ssh.output.WinRmInfraDelegateConfigOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.DelegateResponseData;
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
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
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
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class InfrastructureTaskExecutableStepTest extends CategoryTest {
  @Mock private InfrastructureStepHelper infrastructureStepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ThrowingSupplier throwingSupplier;
  @Mock private StageExecutionHelper stageExecutionHelper;
  @Mock private InfrastructureOutcomeProvider infrastructureOutcomeProvider;
  @Mock InfrastructureValidator infrastructureValidator;
  @Mock private NGLogCallback mockLogCallback;
  @Mock InstanceOutcomeHelper instanceOutcomeHelper;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @InjectMocks private InfrastructureTaskExecutableStep infrastructureStep = new InfrastructureTaskExecutableStep();

  private final String ACCOUNT_ID = "accountId";
  private final Ambiance ambiance =
      Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

  private final AzureConnectorDTO azureConnectorDTO =
      AzureConnectorDTO.builder().delegateSelectors(ImmutableSet.of("delegate1")).build();
  private final ConnectorInfoDTO azureConnectorInfoDTO =
      ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE).connectorConfig(azureConnectorDTO).build();

  private final List<ParameterField<String>> connectorRef =
      Arrays.asList(ParameterField.createValueField("connectorRef"));
  private final AzureSshInfraDelegateConfig azureSshInfraDelegateConfig =
      AzureSshInfraDelegateConfig.sshAzureBuilder().azureConnectorDTO(azureConnectorDTO).build();
  private final AzureWinrmInfraDelegateConfig azureWinrmInfraDelegateConfig =
      AzureWinrmInfraDelegateConfig.winrmAzureBuilder().azureConnectorDTO(azureConnectorDTO).build();
  private final Infrastructure azureInfra = SshWinRmAzureInfrastructure.builder()
                                                .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                                .connectorRef(connectorRef.get(0))
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
          .connectorRef(connectorRef.get(0))
          .credentialsRef(ParameterField.createValueField("sshKeyRef"))
          .region(ParameterField.createValueField("regionId"))
          .awsInstanceFilter(AwsInstanceFilter.builder()
                                 .vpcs(Arrays.asList("vpc1"))
                                 .tags(ParameterField.createValueField(Collections.singletonMap("testTag", "test")))
                                 .build())
          .hostConnectionType(ParameterField.createValueField(HostConnectionTypeKind.HOSTNAME))
          .build();

  AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    when(stepHelper.getEnvironmentType(eq(ambiance))).thenReturn(EnvironmentType.ALL);
    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
    doNothing()
        .when(infrastructureStepHelper)
        .saveInfraExecutionDataToStageInfo(any(Ambiance.class), any(StepResponse.class));
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    doAnswer(im -> im.getArgument(4))
        .when(stageExecutionHelper)
        .saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(any(Ambiance.class), any(ExecutionInfoKey.class),
            any(InfrastructureOutcome.class), any(Set.class), anyString(), anyBoolean(), any(NGLogCallback.class));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithSshAzureInfra() {
    when(infrastructureStepHelper.validateAndGetConnectors(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(Arrays.asList(azureConnectorInfoDTO));
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAzureInfrastructureOutcome.builder()
                        .connectorRef("connectorRef")
                        .subscriptionId("subscriptionId")
                        .hostConnectionType("Hostname")
                        .tags(Map.of("Env", "Dev"))
                        .credentialsRef("sshKeyRef")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("572beaec293c79ba725f68bea0a8a1c7806dc878")
                        .build());
    TaskRequest taskRequest =
        infrastructureStep.obtainTaskAfterRbac(ambiance, azureInfra, StepInputPackage.builder().build());

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
    when(infrastructureStepHelper.validateAndGetConnectors(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(Arrays.asList(awsConnectorInfoDTO));
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAwsInfrastructureOutcome.builder()
                        .connectorRef("connectorRef")
                        .region("region")
                        .hostConnectionType("Hostname")
                        .tags(Map.of("testTag", "test"))
                        .credentialsRef("sshKeyRef")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("70dd2bc5aa8fc8920b04247e4151e8e1074332d3")
                        .build());
    TaskRequest taskRequest =
        infrastructureStep.obtainTaskAfterRbac(ambiance, awsInfra, StepInputPackage.builder().build());

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
    when(infrastructureStepHelper.validateAndGetConnectors(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(Arrays.asList(azureConnectorInfoDTO));
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.WINRM).build());
    when(cdStepHelper.getWinRmInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureWinrmInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAzureInfrastructureOutcome.builder()
                        .connectorRef("connectorRef")
                        .subscriptionId("subscriptionId")
                        .resourceGroup("resourceGroup")
                        .tags(Map.of("Env", "Dev"))
                        .credentialsRef("sshKeyRef")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("572beaec293c79ba725f68bea0a8a1c7806dc878")
                        .build());
    TaskRequest taskRequest =
        infrastructureStep.obtainTaskAfterRbac(ambiance, azureInfra, StepInputPackage.builder().build());

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
    when(infrastructureStepHelper.validateAndGetConnectors(eq(connectorRef), eq(ambiance), any()))
        .thenReturn(Arrays.asList(awsConnectorInfoDTO));
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.WINRM).build());
    when(cdStepHelper.getWinRmInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsWinrmInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAwsInfrastructureOutcome.builder()
                        .connectorRef("connectorRef")
                        .credentialsRef("sshKeyRef")
                        .region("regionId")
                        .tags(Map.of("testTag", "test"))
                        .hostConnectionType("Hostname")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("70dd2bc5aa8fc8920b04247e4151e8e1074332d3")
                        .build());
    TaskRequest taskRequest =
        infrastructureStep.obtainTaskAfterRbac(ambiance, awsInfra, StepInputPackage.builder().build());

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
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAzureInfrastructureOutcome.builder().build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));
    doNothing()
        .when(stageExecutionHelper)
        .saveStageExecutionInfo(eq(ambiance), any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AZURE));
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
    when(stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
             eq(ambiance), any(ExecutionInfoKey.class), any(), any(Set.class), anyString(), anyBoolean(), eq(null)))
        .thenReturn(new HashSet<>(Arrays.asList("host1", "host2")));
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("azure-hosts-response", azureHostsResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);
    when(instanceOutcomeHelper.saveAndGetInstancesOutcome(
             eq(ambiance), any(InfrastructureOutcome.class), any(DelegateResponseData.class), any(Set.class)))
        .thenReturn(getInstancesOutcome());

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

    Collection<StepResponse.StepOutcome> stepOutcomes = response.getStepOutcomes();
    assertThat(stepOutcomes.contains(StepResponse.StepOutcome.builder()
                                         .outcome(getInstancesOutcome())
                                         .name(OutcomeExpressionConstants.INSTANCES)
                                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                                         .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleAwsTaskResultWithSecurityContextSuccess() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(awsSshInfraDelegateConfig);
    when(stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
             eq(ambiance), any(ExecutionInfoKey.class), any(), any(Set.class), anyString(), anyBoolean(), eq(null)))
        .thenReturn(new HashSet<>(Arrays.asList("host1")));
    doNothing()
        .when(stageExecutionHelper)
        .saveStageExecutionInfo(eq(ambiance), any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AWS));
    doNothing()
        .when(stageExecutionHelper)
        .addRollbackArtifactToStageOutcomeIfPresent(eq(ambiance), any(StepResponseBuilder.class),
            any(ExecutionInfoKey.class), eq(InfrastructureKind.SSH_WINRM_AWS));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAwsInfrastructureOutcome.builder()
                                                        .hostConnectionType(HostConnectionTypeKind.PRIVATE_IP)
                                                        .build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));
    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse =
        AwsListEC2InstancesTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .instances(Arrays.asList(AwsEC2Instance.builder().privateIp("10.0.0.1").publicIp("1.1.1.1").build()))
            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("aws-hosts-response", awsListEC2InstancesTaskResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);
    when(instanceOutcomeHelper.saveAndGetInstancesOutcome(
             eq(ambiance), any(InfrastructureOutcome.class), any(DelegateResponseData.class), any(Set.class)))
        .thenReturn(getInstancesOutcome());

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

    Collection<StepResponse.StepOutcome> stepOutcomes = response.getStepOutcomes();
    assertThat(stepOutcomes.contains(StepResponse.StepOutcome.builder()
                                         .outcome(getInstancesOutcome())
                                         .name(OutcomeExpressionConstants.INSTANCES)
                                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                                         .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleAzureTaskResultWithSecurityContextFailure() throws Exception {
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(azureSshInfraDelegateConfig);
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAzureInfrastructureOutcome.builder().build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));

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
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAwsInfrastructureOutcome.builder().build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));

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
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAzureInfrastructureOutcome.builder().build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));

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
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(InfrastructureTaskExecutableStepSweepingOutput.builder()
                             .infrastructureOutcome(SshWinRmAwsInfrastructureOutcome.builder().build())
                             .skipInstances(true)
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_TASK_EXECUTABLE_STEP_OUTPUT));

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
        .validateExpression(any(), eq(credentialsRef), any(), any());

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");

    ParameterField connectorRef2 = new ParameterField<>(null, null, true, "expression2", null, true);
    builder.connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
        .credentialsRef(ParameterField.createValueField("value"))
        .build();
    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(connectorRef2), any(), any(), any());

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
    List<ParameterField<String>> gcpSaConnectorRefs = Arrays.asList(ParameterField.createValueField("account.gcp-sa"));
    List<ParameterField<String>> gcpDelegateConnectorRefs =
        Arrays.asList(ParameterField.createValueField("account.gcp-delegate"));
    List<ParameterField<String>> missingConnectorRefs =
        Arrays.asList(ParameterField.createValueField("account.missing"));

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
                format("Connector not found for identifier : [%s]", missingConnectorRefs.get(0).getValue())))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(missingConnectorRefs), eq(ambiance), any());

    doReturn(Arrays.asList(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(gcpSaConnectorRefs), eq(ambiance), any());

    doReturn(Arrays.asList(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorInheritFromDelegate).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(gcpDelegateConnectorRefs), eq(ambiance), any());

    assertConnectorValidationMessage(K8sGcpInfrastructure.builder().connectorRef(missingConnectorRefs.get(0)).build(),
        "Connector not found for identifier : [account.missing]");

    assertThatCode(()
                       -> infrastructureStep.validateConnector(
                           K8sGcpInfrastructure.builder().connectorRef(gcpSaConnectorRefs.get(0)).build(), ambiance,
                           mockLogCallback))
        .doesNotThrowAnyException();

    assertThatCode(()
                       -> infrastructureStep.validateConnector(
                           K8sGcpInfrastructure.builder().connectorRef(gcpDelegateConnectorRefs.get(0)).build(),
                           ambiance, mockLogCallback))
        .doesNotThrowAnyException();
  }

  private void assertConnectorValidationMessage(Infrastructure infrastructure, String message) {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance, mockLogCallback))
        .hasMessageContaining(message);
  }

  private InstancesOutcome getInstancesOutcome() {
    return InstancesOutcome.builder()
        .instances(List.of(InstanceOutcome.builder().name("instanceName").hostName("instanceHostname").build()))
        .build();
  }
}
