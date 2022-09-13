/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.task.ssh.AwsSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.StepHelper;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.shellscript.HostsOutput;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.YamlPipelineUtils;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InfrastructureTaskExecutableStepV2Test {
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private InfrastructureStepHelper infrastructureStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private StageExecutionHelper stageExecutionHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private NGLogCallback logCallback;
  @Mock private ThrowingSupplier<DelegateResponseData> throwingSupplier;
  @InjectMocks private InfrastructureTaskExecutableStepV2 step = new InfrastructureTaskExecutableStepV2();
  private AutoCloseable mocks;

  private final Ambiance ambiance = buildAmbiance();
  private final InfrastructureConfig awsInfra = testAwsInfra();
  private final InfrastructureConfig azureInfra = testAzureInfra();
  private final InfrastructureConfig pdcInfra = testPdcInfra();

  @Before
  public void setUp() throws Exception {
    this.mocks = MockitoAnnotations.openMocks(this);
    doReturn(ServiceStepOutcome.builder().type("ssh").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject("service")));

    doReturn(EnvironmentOutcome.builder().type(EnvironmentType.PreProduction).build())
        .when(sweepingOutputService)
        .resolve(any(), eq(RefObjectUtils.getSweepingOutputRefObject("env")));

    doReturn(io.harness.beans.EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any(Ambiance.class));

    doReturn("bytes".getBytes()).when(kryoSerializer).asDeflatedBytes(any());

    doReturn(logCallback).when(infrastructureStepHelper).getInfrastructureLogCallback(any(Ambiance.class));

    // return all the hosts passed as is
    when(stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
             any(Ambiance.class), any(ExecutionInfoKey.class), any(), any(Set.class), anyString(), anyBoolean(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(3, Set.class));
  }

  @After
  public void tearDown() throws Exception {
    if (this.mocks != null) {
      this.mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskInfraNotFound() {
    doReturn(Optional.empty())
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainTask(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .build(),
                            null))
        .withMessageContaining("not found")
        .withMessageContaining("infra-id")
        .withMessageContaining("env-id");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForInvalidStepParams_0() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainTask(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(null)
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .build(),
                            null))
        .withMessageContaining("environment");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForInvalidStepParams_1() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainTask(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(null)
                                .build(),
                            null))
        .withMessageContaining("infrastructure definition");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForInvalidStepParams_2() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.obtainTask(ambiance,
                    InfrastructureTaskExecutableStepV2Params.builder()
                        .envRef(ParameterField.<String>builder().expression(true).expressionValue("<+foo>").build())
                        .infraRef(ParameterField.createValueField("infra-id"))
                        .build(),
                    null))
        .withMessageContaining("environment")
        .withMessageContaining("resolved")
        .withMessageContaining("<+foo>");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForInvalidStepParams_3() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.obtainTask(ambiance,
                    InfrastructureTaskExecutableStepV2Params.builder()
                        .envRef(ParameterField.createValueField("env-id"))
                        .infraRef(ParameterField.<String>builder().expression(true).expressionValue("<+foo>").build())
                        .build(),
                    null))
        .withMessageContaining("infrastructure")
        .withMessageContaining("resolved")
        .withMessageContaining("<+foo>");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskPermissionDenied() {
    mockInfra(pdcInfra);

    doThrow(AccessDeniedException.class)
        .when(pipelineRbacHelper)
        .checkRuntimePermissions(any(Ambiance.class), any(Set.class));
    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(()
                        -> step.obtainTask(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .build(),
                            null));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForNonTaskTypeInfra() {
    mockInfra(pdcInfra);
    TaskRequest taskRequest = step.obtainTask(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        null);
    assertThat(taskRequest).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForTaskInfra_AwsSsh() {
    doReturn(
        AwsSshInfraDelegateConfig.sshAwsBuilder()
            .awsConnectorDTO(
                AwsConnectorDTO.builder()
                    .credential(
                        AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                AwsInheritFromDelegateSpecDTO.builder().delegateSelectors(Set.of("us-east")).build())
                            .build())
                    .delegateSelectors(Set.of("ecs"))
                    .build())
            .build())
        .when(cdStepHelper)
        .getSshInfraDelegateConfig(any(InfrastructureOutcome.class), any(Ambiance.class));

    mockInfra(awsInfra);
    TaskRequest taskRequest = step.obtainTask(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        null);

    assertThat(taskRequest.getTaskCategory()).isEqualTo(TaskCategory.DELEGATE_TASK_V2);
    DelegateTaskRequest delegateTaskRequest = taskRequest.getDelegateTaskRequest();

    assertThat(delegateTaskRequest.getUnitsCount()).isEqualTo(1);
    assertThat(delegateTaskRequest.getUnits(0)).isEqualTo("Execute");
    assertThat(delegateTaskRequest.getTaskName()).isEqualTo("NG_AWS_TASK");

    verifyTaskRequest(delegateTaskRequest.getRequest());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void obtainTaskForTaskInfra_SshAzure() {
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder()
                                         .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                         .credential(AzureCredentialDTO.builder()
                                                         .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                                                         .config(AzureInheritFromDelegateDetailsDTO.builder().build())
                                                         .build())
                                         .build();
    doReturn(AzureSshInfraDelegateConfig.sshAzureBuilder().azureConnectorDTO(connectorDTO).build())
        .when(cdStepHelper)
        .getSshInfraDelegateConfig(any(InfrastructureOutcome.class), any(Ambiance.class));

    mockInfra(azureInfra);

    doReturn(ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE).connectorConfig(connectorDTO).build())
        .when(infrastructureStepHelper)
        .validateAndGetConnector(
            eq(ParameterField.createValueField("azureconnector")), any(Ambiance.class), any(NGLogCallback.class));
    TaskRequest taskRequest = step.obtainTask(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        null);

    assertThat(taskRequest.getTaskCategory()).isEqualTo(TaskCategory.DELEGATE_TASK_V2);
    DelegateTaskRequest delegateTaskRequest = taskRequest.getDelegateTaskRequest();

    assertThat(delegateTaskRequest.getUnitsCount()).isEqualTo(1);
    assertThat(delegateTaskRequest.getUnits(0)).isEqualTo("Execute");
    assertThat(delegateTaskRequest.getTaskName()).isEqualTo("NG_AZURE_TASK");

    verifyTaskRequest(delegateTaskRequest.getRequest());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponseForTaskInfra_AwsSSH() throws Exception {
    SshWinRmAwsInfrastructure spec = (SshWinRmAwsInfrastructure) awsInfra.getInfrastructureDefinitionConfig().getSpec();
    mockInfra(awsInfra);
    mockInfraTaskExecutableSweepingOutput(SshWinRmAwsInfrastructureOutcome.builder()
                                              .connectorRef(spec.getConnectorRef().getValue())
                                              .credentialsRef(spec.getCredentialsRef().getValue())
                                              .region(spec.getRegion().getValue())
                                              .build());

    doReturn(AwsListEC2InstancesTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .instances(List.of(mockAwsInstance("i1"), mockAwsInstance("i2")))
                 .build())
        .when(throwingSupplier)
        .get();

    StepResponse stepResponse = step.handleTaskResult(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        throwingSupplier);

    // verify unit progress data
    assertThat(stepResponse.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(SshWinRmAwsInfrastructureOutcome.builder()
                       .region("us-east-2")
                       .connectorRef("awsconnector")
                       .credentialsRef("sshkey")
                       .build());

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ExecutionSweepingOutput> outputCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), stringCaptor.capture(), outputCaptor.capture(), anyString());

    List<ExecutionSweepingOutput> allOutputs = outputCaptor.getAllValues();
    List<String> outputNames = stringCaptor.getAllValues();

    assertThat(allOutputs).containsExactly(HostsOutput.builder().hosts(Set.of("public-i1", "public-i2")).build());
    assertThat(outputNames).containsExactly("output");

    // verify some more method calls
    verify(stageExecutionHelper, times(1))
        .saveStageExecutionInfoAndPublishExecutionInfoKey(
            any(Ambiance.class), any(ExecutionInfoKey.class), eq("SshWinRmAws"));
    verify(stageExecutionHelper, times(1))
        .addRollbackArtifactToStageOutcomeIfPresent(
            any(Ambiance.class), any(StepResponseBuilder.class), any(ExecutionInfoKey.class), eq("SshWinRmAws"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponseForTaskInfra_Azure() throws Exception {
    SshWinRmAzureInfrastructure spec =
        (SshWinRmAzureInfrastructure) azureInfra.getInfrastructureDefinitionConfig().getSpec();
    mockInfra(azureInfra);
    mockInfraTaskExecutableSweepingOutput(SshWinRmAzureInfrastructureOutcome.builder()
                                              .credentialsRef(spec.getCredentialsRef().getValue())
                                              .connectorRef(spec.getConnectorRef().getValue())
                                              .resourceGroup(spec.getResourceGroup().getValue())
                                              .subscriptionId(spec.getSubscriptionId().getValue())
                                              .build());

    doReturn(AzureHostsResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .hosts(List.of(AzureHostResponse.builder().hostName("h1").build()))
                 .build())
        .when(throwingSupplier)
        .get();

    StepResponse stepResponse = step.handleTaskResult(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        throwingSupplier);

    // verify unit progress data
    assertThat(stepResponse.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(SshWinRmAzureInfrastructureOutcome.builder()
                       .connectorRef("azureconnector")
                       .subscriptionId("dev-subscription")
                       .resourceGroup("harnessdev")
                       .credentialsRef("sshkey")
                       .build());

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ExecutionSweepingOutput> outputCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), stringCaptor.capture(), outputCaptor.capture(), anyString());

    List<ExecutionSweepingOutput> allOutputs = outputCaptor.getAllValues();
    List<String> outputNames = stringCaptor.getAllValues();

    assertThat(allOutputs).containsExactly(HostsOutput.builder().hosts(Set.of("h1")).build());
    assertThat(outputNames).containsExactly("output");

    // verify some more method calls
    verify(stageExecutionHelper, times(1))
        .saveStageExecutionInfoAndPublishExecutionInfoKey(
            any(Ambiance.class), any(ExecutionInfoKey.class), eq("SshWinRmAzure"));
    verify(stageExecutionHelper, times(1))
        .addRollbackArtifactToStageOutcomeIfPresent(
            any(Ambiance.class), any(StepResponseBuilder.class), any(ExecutionInfoKey.class), eq("SshWinRmAzure"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponseNonTaskInfra() throws Exception {
    PdcInfrastructure spec = (PdcInfrastructure) pdcInfra.getInfrastructureDefinitionConfig().getSpec();
    mockInfra(pdcInfra);
    mockInfraTaskExecutableSweepingOutput(PdcInfrastructureOutcome.builder()
                                              .credentialsRef(spec.getCredentialsRef().getValue())
                                              .connectorRef(spec.getConnectorRef().getValue())
                                              .hosts(List.of("h1", "h2"))
                                              .build());
    doReturn(PdcSshInfraDelegateConfig.builder().hosts(Set.of("h1", "h2")).build())
        .when(cdStepHelper)
        .getSshInfraDelegateConfig(any(InfrastructureOutcome.class), any(Ambiance.class));

    StepResponse stepResponse = step.handleTaskResult(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        throwingSupplier);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(PdcInfrastructureOutcome.builder()
                       .hosts(List.of("h1", "h2"))
                       .connectorRef("awsconnector")
                       .credentialsRef("sshkey")
                       .build());

    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("output"), eq(HostsOutput.builder().hosts(Set.of("h1", "h2")).build()),
            eq("STAGE"));
  }

  private AwsEC2Instance mockAwsInstance(String id) {
    return AwsEC2Instance.builder().instanceId(id).publicDnsName("public-" + id).build();
  }

  private void verifyTaskRequest(SubmitTaskRequest request) {
    assertThat(request.getAccountId().getId()).isEqualTo("ACCOUNT_ID");
    assertThat(request.getSelectionTrackingLogEnabled()).isTrue();
    assertThat(request.getLogAbstractions().getValuesMap().keySet())
        .containsExactly("accountId", "orgId", "projectId", "pipelineId", "runSequence", "level0");
    assertThat(request.getLogAbstractions().getValuesMap().values())
        .containsExactly("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "", "0", "");
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(ManifestsStepV2.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(UUIDGenerator.generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", "ACCOUNT_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("principal")
                                               .setPrincipalType(PrincipalType.USER)
                                               .setShouldValidateRbac(true)
                                               .build())
                         .build())
        .build();
  }

  private void mockInfra(InfrastructureConfig infra) {
    InfrastructureEntity entity = InfrastructureEntity.builder()
                                      .accountId("accountId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projectId")
                                      .identifier("infra-id")
                                      .envIdentifier("env-id")
                                      .type(infra.getInfrastructureDefinitionConfig().getType())
                                      .yaml(YamlPipelineUtils.writeYamlString(infra))
                                      .build();
    doReturn(Optional.ofNullable(entity))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  private void mockInfraTaskExecutableSweepingOutput(InfrastructureOutcome outcome) {
    doReturn(
        OptionalSweepingOutput.builder()
            .found(true)
            .output(InfrastructureTaskExecutableStepSweepingOutput.builder().infrastructureOutcome(outcome).build())
            .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                OutcomeExpressionConstants.INFRA_TASK_EXECUTABLE_STEP_OUTPUT)));
  }

  private InfrastructureConfig testPdcInfra() {
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(
            InfrastructureDefinitionConfig.builder()
                .type(InfrastructureType.PDC)
                .spec(PdcInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("awsconnector"))
                          .credentialsRef(ParameterField.createValueField("sshkey"))
                          .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                          .build())
                .build())
        .build();
  }

  private InfrastructureConfig testAzureInfra() {
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(
            InfrastructureDefinitionConfig.builder()
                .type(InfrastructureType.SSH_WINRM_AZURE)
                .spec(SshWinRmAzureInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("azureconnector"))
                          .credentialsRef(ParameterField.createValueField("sshkey"))
                          .resourceGroup(ParameterField.createValueField("harnessdev"))
                          .subscriptionId(ParameterField.createValueField("dev-subscription"))
                          .build())
                .build())
        .build();
  }

  private InfrastructureConfig testAwsInfra() {
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(
            InfrastructureDefinitionConfig.builder()
                .type(InfrastructureType.SSH_WINRM_AWS)
                .spec(SshWinRmAwsInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("awsconnector"))
                          .credentialsRef(ParameterField.createValueField("sshkey"))
                          .region(ParameterField.createValueField("us-east-2"))
                          .awsInstanceFilter(AwsInstanceFilter.builder().vpcs(List.of("vpc1")).build())
                          .build())
                .build())
        .build();
  }
}
