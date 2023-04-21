/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.ng.core.environment.beans.EnvironmentType.PreProduction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariableType;
import io.harness.cdng.customdeployment.CustomDeploymentNumberNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentSecretNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentStringNGVariable;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.InfrastructureOutcomeProvider;
import io.harness.cdng.infra.InfrastructureValidator;
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.instance.InstanceOutcomeHelper;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.HostsOutput;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.UUIDGenerator;
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
import io.harness.delegate.task.ssh.EmptyHostDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class InfrastructureTaskExecutableStepV2Test extends CategoryTest {
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private InfrastructureOutcomeProvider infrastructureOutcomeProvider;
  @Mock InfrastructureValidator infrastructureValidator;
  @Mock private InfrastructureStepHelper infrastructureStepHelper;
  @Mock private CDStepHelper cdStepHelper = Mockito.spy(CDStepHelper.class);
  @Mock private StepHelper stepHelper;
  @Mock private StageExecutionHelper stageExecutionHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private NGLogCallback logCallback;
  @Mock private CDExpressionResolver resolver;
  @Spy InstanceOutcomeHelper instanceOutcomeHelper;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks private InfrastructureTaskExecutableStepV2 step = new InfrastructureTaskExecutableStepV2();
  private AutoCloseable mocks;

  private final Ambiance ambiance = buildAmbiance();
  private final InfrastructureConfig awsInfra = testAwsInfra();
  private final InfrastructureConfig azureInfra = testAzureInfra();
  private final InfrastructureConfig pdcInfra = testPdcInfra();
  private final InfrastructureConfig pdcInfraWithInputs = testPdcInfraWithInputs();

  @Before
  public void setUp() throws Exception {
    this.mocks = MockitoAnnotations.openMocks(this);

    doReturn(ServiceStepOutcome.builder().type("ssh").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject("service")));

    doReturn(EnvironmentOutcome.builder().type(PreProduction).build())
        .when(sweepingOutputService)
        .resolve(any(), eq(RefObjectUtils.getSweepingOutputRefObject("env")));

    doReturn(io.harness.beans.EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any(Ambiance.class));

    doReturn("bytes".getBytes()).when(kryoSerializer).asDeflatedBytes(any());

    doReturn(logCallback)
        .when(infrastructureStepHelper)
        .getInfrastructureLogCallback(any(Ambiance.class), eq(true), eq("Execute"));

    // return all the hosts passed as is
    when(stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
             any(Ambiance.class), any(ExecutionInfoKey.class), any(), any(Set.class), anyString(), anyBoolean(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(3, Set.class));

    Mockito.doReturn("taskId").when(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), any());

    doNothing()
        .when(infrastructureStepHelper)
        .saveInfraExecutionDataToStageInfo(any(Ambiance.class), any(StepResponse.class));
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    doCallRealMethod().when(cdStepHelper).mapTaskRequestToDelegateTaskRequest(any(), any(), anySet());
    doCallRealMethod()
        .when(cdStepHelper)
        .mapTaskRequestToDelegateTaskRequest(any(), any(), anySet(), anyString(), anyBoolean());
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
                        -> step.executeAsyncAfterRbac(ambiance,
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
  public void obtainTaskInfraTypeMismatch() {
    doReturn(Optional.of(InfrastructureEntity.builder()
                             .type(InfrastructureType.KUBERNETES_DIRECT)
                             .deploymentType(ServiceDefinitionType.KUBERNETES)
                             .build()))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.executeAsyncAfterRbac(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .deploymentType(ServiceDefinitionType.AZURE_WEBAPP)
                                .build(),
                            null))
        .withMessageContaining(
            "Deployment type of the stage [AzureWebApp] and the infrastructure [Kubernetes] do not match");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void executeAsyncInvalidStepParams_0() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.executeAsyncAfterRbac(ambiance,
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
  public void executeAsyncInvalidStepParams_1() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.executeAsyncAfterRbac(ambiance,
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
  public void executeAsyncInvalidStepParams_2() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.executeAsyncAfterRbac(ambiance,
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
  public void executeAsyncInvalidStepParams_3() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.executeAsyncAfterRbac(ambiance,
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
        .checkRuntimePermissions(any(Ambiance.class), any(List.class), anyBoolean());
    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(()
                        -> step.executeAsyncAfterRbac(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .build(),
                            null));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void executeAsyncNonTaskTypeInfra() throws IOException {
    mockInfra(pdcInfraWithInputs);
    String inputYaml = "identifier: \"infra-id\"\n"
        + "type: \"Pdc\"\n"
        + "spec:\n"
        + "  credentialsRef: \"qa\"";
    AsyncExecutableResponse asyncExecutableResponse = step.executeAsyncAfterRbac(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .infraInputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
            .build(),
        null);
    assertThat(asyncExecutableResponse.getLogKeysCount()).isEqualTo(1);
    assertThat(asyncExecutableResponse.getLogKeys(0))
        .isEqualTo(
            "accountId:ACCOUNT_ID/orgId:ORG_ID/projectId:PROJECT_ID/pipelineId:/runSequence:0/level0:infrastructure-commandUnit:Execute");

    verify(resolver, times(1)).updateExpressions(any(Ambiance.class), any(Infrastructure.class));
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void testInfraInputsMerge() {
    mockInfra(pdcInfraWithInputs);
    String inputYaml = "identifier: \"infra-id\"\n"
        + "type: \"Pdc\"\n"
        + "spec:\n"
        + "  credentialsRef: \"prod\"";
    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.executeAsyncAfterRbac(ambiance,
                            InfrastructureTaskExecutableStepV2Params.builder()
                                .envRef(ParameterField.createValueField("env-id"))
                                .infraRef(ParameterField.createValueField("infra-id"))
                                .infraInputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
                                .build(),
                            null))
        .withMessageContaining("The value provided prod does not match any of the allowed values [dev,qa]");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void executeAsyncTaskInfra_AwsSsh() {
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
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAwsInfrastructureOutcome.builder()
                        .connectorRef("awsconnector")
                        .hostConnectionType("PrivateIP")
                        .region("us-east-2")
                        .credentialsRef("sshkey")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("7d998370da30e12c5384378d730ccf14676ce6f9")
                        .build());
    mockInfra(awsInfra);
    AsyncExecutableResponse asyncExecutableResponse = step.executeAsyncAfterRbac(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        null);

    verify(resolver, times(1)).updateExpressions(any(Ambiance.class), any(Infrastructure.class));

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(captor.capture(), eq(Duration.ZERO));

    DelegateTaskRequest delegateTaskRequest = captor.getValue();

    assertThat(delegateTaskRequest.getTaskType()).isEqualTo("NG_AWS_TASK");

    verifyTaskRequest(delegateTaskRequest);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category({UnitTests.class})
  public void executeAsyncTaskInfra_SshAzure() {
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
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SshWinRmAzureInfrastructureOutcome.builder()
                        .connectorRef("azureconnector")
                        .subscriptionId("dev-subscription")
                        .resourceGroup("harnessdev")
                        .credentialsRef("sshkey")
                        .environment(EnvironmentOutcome.builder().build())
                        .infrastructureKey("ce1748ef0412b8f89d44b1297a633af41bfbd4a6")
                        .build());
    doReturn(Arrays.asList(
                 ConnectorInfoDTO.builder().connectorType(ConnectorType.AZURE).connectorConfig(connectorDTO).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(List.of(ParameterField.createValueField("azureconnector"))), any(Ambiance.class),
            any(NGLogCallback.class));

    AsyncExecutableResponse asyncExecutableResponse = step.executeAsyncAfterRbac(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        null);

    verify(resolver, times(1)).updateExpressions(any(Ambiance.class), any(Infrastructure.class));

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(captor.capture(), eq(Duration.ZERO));

    DelegateTaskRequest delegateTaskRequest = captor.getValue();

    assertThat(delegateTaskRequest.getTaskType()).isEqualTo("NG_AZURE_TASK");

    verifyTaskRequest(delegateTaskRequest);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponseForTaskInfra_AwsSSH() {
    SshWinRmAwsInfrastructure spec = (SshWinRmAwsInfrastructure) awsInfra.getInfrastructureDefinitionConfig().getSpec();
    mockInfra(awsInfra);
    mockInfraTaskExecutableSweepingOutput(SshWinRmAwsInfrastructureOutcome.builder()
                                              .connectorRef(spec.getConnectorRef().getValue())
                                              .credentialsRef(spec.getCredentialsRef().getValue())
                                              .region(spec.getRegion().getValue())
                                              .hostConnectionType(HostConnectionTypeKind.PUBLIC_IP)
                                              .build());
    mockSaveAndGetInstancesOutcomeForTaskStep();

    StepResponse stepResponse = step.handleAsyncResponse(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        Map.of("taskId",
            AwsListEC2InstancesTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .instances(List.of(mockAwsInstance("1"), mockAwsInstance("2")))
                .build()));

    // verify unit progress data
    assertThat(stepResponse.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(SshWinRmAwsInfrastructureOutcome.builder()
                       .region("us-east-2")
                       .connectorRef("awsconnector")
                       .credentialsRef("sshkey")
                       .hostConnectionType(HostConnectionTypeKind.PUBLIC_IP)
                       .build());

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ExecutionSweepingOutput> outputCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), stringCaptor.capture(), outputCaptor.capture(), anyString());

    List<ExecutionSweepingOutput> allOutputs = outputCaptor.getAllValues();
    List<String> outputNames = stringCaptor.getAllValues();

    assertThat(allOutputs).containsExactly(HostsOutput.builder().hosts(Set.of("1.1.1.1", "1.1.1.2")).build());
    assertThat(outputNames).containsExactly("output");

    // verify some more method calls
    verify(stageExecutionHelper, times(1))
        .saveStageExecutionInfo(any(Ambiance.class), any(ExecutionInfoKey.class), eq("SshWinRmAws"));
    verify(stageExecutionHelper, times(1))
        .addRollbackArtifactToStageOutcomeIfPresent(
            any(Ambiance.class), any(StepResponseBuilder.class), any(ExecutionInfoKey.class), eq("SshWinRmAws"));

    Collection<StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes)
        .containsAnyOf(StepOutcome.builder()
                           .outcome(getInstancesOutcome())
                           .name(OutcomeExpressionConstants.INSTANCES)
                           .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                           .build());
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
    mockSaveAndGetInstancesOutcomeForTaskStep();

    StepResponse stepResponse = step.handleAsyncResponse(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        Map.of("taskId",
            AzureHostsResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .hosts(List.of(AzureHostResponse.builder().hostName("h1").build()))
                .build()));

    // verify unit progress data
    assertThat(stepResponse.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
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
        .saveStageExecutionInfo(any(Ambiance.class), any(ExecutionInfoKey.class), eq("SshWinRmAzure"));
    verify(stageExecutionHelper, times(1))
        .addRollbackArtifactToStageOutcomeIfPresent(
            any(Ambiance.class), any(StepResponseBuilder.class), any(ExecutionInfoKey.class), eq("SshWinRmAzure"));

    Collection<StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes)
        .containsAnyOf(StepOutcome.builder()
                           .outcome(getInstancesOutcome())
                           .name(OutcomeExpressionConstants.INSTANCES)
                           .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                           .build());
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
    mockSaveAndGetInstancesOutcomeForNonTaskStep();

    StepResponse stepResponse =
        step.handleAsyncResponse(ambiance, InfrastructureTaskExecutableStepV2Params.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
    Iterator<StepOutcome> iterator = stepResponse.getStepOutcomes().iterator();
    assertThat(iterator.next().getOutcome()).isEqualTo(getInstancesOutcome());
    assertThat(iterator.next().getOutcome())
        .isEqualTo(PdcInfrastructureOutcome.builder()
                       .hosts(List.of("h1", "h2"))
                       .connectorRef("awsconnector")
                       .credentialsRef("sshkey")
                       .build());

    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("output"), eq(HostsOutput.builder().hosts(Set.of("h1", "h2")).build()),
            eq("STAGE"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testExceptionInHandleResponse() {
    // introduce an exception
    doReturn(null).when(sweepingOutputService).resolve(any(Ambiance.class), any());

    StepResponse stepResponse = step.handleAsyncResponse(buildAmbiance(), null, null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    FailureData failureData = stepResponse.getFailureInfo().getFailureData(0);

    assertThat(failureData.getCode()).isEqualTo("GENERAL_ERROR");
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testHandleResponseForCustomDeploymentArtifactOutCome() {
    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.CUSTOM_DEPLOYMENT).build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject("service")));

    doNothing()
        .when(stageExecutionHelper)
        .addRollbackArtifactToStageOutcomeIfPresent(eq(ambiance), any(StepResponseBuilder.class),
            any(ExecutionInfoKey.class), eq(InfrastructureKind.CUSTOM_DEPLOYMENT));

    StepTemplateRef stepTemplateRef = StepTemplateRef.builder().templateRef("openstack").versionLabel("v1").build();
    List<CustomDeploymentNGVariable> variables = new ArrayList<>();
    CustomDeploymentNumberNGVariable numberNGVariable = CustomDeploymentNumberNGVariable.builder()
                                                            .name("number")
                                                            .type(CustomDeploymentNGVariableType.NUMBER)
                                                            .value(ParameterField.<Double>builder().value(10.0).build())
                                                            .build();
    CustomDeploymentStringNGVariable stringNGVariable =
        CustomDeploymentStringNGVariable.builder()
            .name("url")
            .type(CustomDeploymentNGVariableType.NUMBER)
            .value(ParameterField.<String>builder().value("url").build())
            .build();

    CustomDeploymentSecretNGVariable secretNGVariable =
        CustomDeploymentSecretNGVariable.builder()
            .name("token")
            .type(CustomDeploymentNGVariableType.SECRET)
            .value(ParameterField.<SecretRefData>builder()
                       .value(SecretRefData.builder().identifier("secretId").scope(Scope.ACCOUNT).build())
                       .build())
            .build();

    variables.add(numberNGVariable);
    variables.add(stringNGVariable);
    variables.add(secretNGVariable);

    InfrastructureConfig customDeploymentInfra =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(InfrastructureDefinitionConfig.builder()
                                                .type(InfrastructureType.CUSTOM_DEPLOYMENT)
                                                .spec(CustomDeploymentInfrastructure.builder()
                                                          .customDeploymentRef(stepTemplateRef)
                                                          .variables(variables)
                                                          .build())
                                                .build())
            .build();

    InfrastructureEntity entity = InfrastructureEntity.builder()
                                      .accountId("accountId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projectId")
                                      .identifier("infra-id")
                                      .envIdentifier("env-id")
                                      .type(customDeploymentInfra.getInfrastructureDefinitionConfig().getType())
                                      .yaml(YamlPipelineUtils.writeYamlString(customDeploymentInfra))
                                      .build();

    doReturn(Optional.ofNullable(entity))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());

    CustomDeploymentInfrastructure spec =
        (CustomDeploymentInfrastructure) customDeploymentInfra.getInfrastructureDefinitionConfig().getSpec();
    mockInfraTaskExecutableSweepingOutput(
        CustomDeploymentInfrastructureOutcome.builder()
            .variables(spec.getVariables().stream().collect(
                Collectors.toMap(CustomDeploymentNGVariable::getName, CustomDeploymentNGVariable::getCurrentValue)))
            .instanceFetchScript("echo test")
            .instanceAttributes(new HashMap<>())
            .build());

    mockSaveAndGetInstancesOutcomeForTaskStep();

    doReturn(EmptyHostDelegateConfig.builder().hosts(Collections.emptySet()).build())
        .when(cdStepHelper)
        .getSshInfraDelegateConfig(any(), any());

    StepResponse stepResponse = step.handleAsyncResponse(ambiance,
        InfrastructureTaskExecutableStepV2Params.builder()
            .envRef(ParameterField.createValueField("env-id"))
            .infraRef(ParameterField.createValueField("infra-id"))
            .build(),
        Collections.emptyMap());

    // verify unit progress data
    assertThat(stepResponse.getUnitProgressList().get(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);

    Iterator<StepOutcome> iterator = stepResponse.getStepOutcomes().iterator();
    Outcome outcome = iterator.next().getOutcome();
    assertThat(outcome).isInstanceOf(InstancesOutcome.class);
    InstancesOutcome customDeploymentInstances = (InstancesOutcome) outcome;
    assertThat(customDeploymentInstances.getInstances()).isEmpty();

    outcome = iterator.next().getOutcome();
    assertThat(outcome).isInstanceOf(CustomDeploymentInfrastructureOutcome.class);

    CustomDeploymentInfrastructureOutcome stepOutCome = (CustomDeploymentInfrastructureOutcome) outcome;
    assertThat(stepOutCome.getInstanceFetchScript()).isEqualTo("echo test");
    assertThat(stepOutCome.getVariables().size()).isEqualTo(3);

    Map<String, Object> outComeVariables = stepOutCome.getVariables();
    ParameterField<Double> number = (ParameterField<Double>) outComeVariables.get("number");
    ParameterField<String> url = (ParameterField<String>) outComeVariables.get("url");
    ParameterField<SecretRefData> token = (ParameterField<SecretRefData>) outComeVariables.get("token");

    assertThat(number.getValue()).isEqualTo(10.0);
    assertThat(url.getValue()).isEqualTo("url");
    assertThat(token.getValue().getIdentifier()).isEqualTo("secretId");
    assertThat(token.getValue().getScope()).isEqualTo(Scope.ACCOUNT);
    // verify some more method calls
    verify(stageExecutionHelper, times(1))
        .saveStageExecutionInfo(
            any(Ambiance.class), any(ExecutionInfoKey.class), eq(InfrastructureKind.CUSTOM_DEPLOYMENT));
    verify(stageExecutionHelper, times(1))
        .addRollbackArtifactToStageOutcomeIfPresent(any(Ambiance.class), any(StepResponseBuilder.class),
            any(ExecutionInfoKey.class), eq(InfrastructureKind.CUSTOM_DEPLOYMENT));

    ArgumentCaptor<SshInfraDelegateConfigOutput> delegateConfigOutput =
        ArgumentCaptor.forClass(SshInfraDelegateConfigOutput.class);
    verify(sweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            delegateConfigOutput.capture(), eq(StepCategory.STAGE.name()));

    SshInfraDelegateConfigOutput delegateConfigOutputValue = delegateConfigOutput.getValue();
    assertThat(delegateConfigOutputValue).isNotNull();
    assertThat(delegateConfigOutputValue.getSshInfraDelegateConfig()).isNotNull();
    SshInfraDelegateConfig sshInfraDelegateConfig = delegateConfigOutputValue.getSshInfraDelegateConfig();
    assertThat(sshInfraDelegateConfig).isInstanceOf(EmptyHostDelegateConfig.class);
    EmptyHostDelegateConfig emptyHostDelegateConfig = (EmptyHostDelegateConfig) sshInfraDelegateConfig;
    assertThat(emptyHostDelegateConfig.getHosts()).isEmpty();
  }

  private AwsEC2Instance mockAwsInstance(String id) {
    return AwsEC2Instance.builder().instanceId(id).privateIp("10.0.0." + id).publicIp("1.1.1." + id).build();
  }

  private void verifyTaskRequest(DelegateTaskRequest request) {
    assertThat(request.getAccountId()).isEqualTo("ACCOUNT_ID");
    assertThat(request.getLogStreamingAbstractions()
                   .entrySet()
                   .stream()
                   .map(e -> e.getKey() + ":" + e.getValue())
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("orgId:ORG_ID", "pipelineId:", "runSequence:0", "level0:infrastructure",
            "accountId:ACCOUNT_ID", "projectId:PROJECT_ID");
    assertThat(request.getTaskSetupAbstractions()
                   .entrySet()
                   .stream()
                   .map(e -> e.getKey() + ":" + e.getValue())
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("orgIdentifier:ORG_ID", "owner:ORG_ID/PROJECT_ID", "envType:NON_PROD", "ng:true",
            "accountId:ACCOUNT_ID", "projectIdentifier:PROJECT_ID");
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(InfrastructureTaskExecutableStepV2.STEP_TYPE)
                   .setIdentifier("infrastructure")
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
                .orgIdentifier("orgId")
                .projectIdentifier("projectId")
                .identifier("infra-id")
                .type(InfrastructureType.PDC)
                .spec(PdcInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("awsconnector"))
                          .credentialsRef(ParameterField.createValueField("sshkey"))
                          .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                          .build())
                .build())
        .build();
  }

  private InfrastructureConfig testPdcInfraWithInputs() {
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(
            InfrastructureDefinitionConfig.builder()
                .orgIdentifier("orgId")
                .projectIdentifier("projectId")
                .identifier("infra-id")
                .type(InfrastructureType.PDC)
                .spec(PdcInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("awsconnector"))
                          .credentialsRef(
                              ParameterField.createExpressionField(true, "<+input>.allowedValues(dev, qa)", null, true))
                          .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                          .build())
                .build())
        .build();
  }

  private InfrastructureConfig testAzureInfra() {
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(
            InfrastructureDefinitionConfig.builder()
                .orgIdentifier("orgId")
                .projectIdentifier("projectId")
                .identifier("infra-id")
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
                .orgIdentifier("orgId")
                .projectIdentifier("projectId")
                .identifier("infra-id")
                .type(InfrastructureType.SSH_WINRM_AWS)
                .spec(SshWinRmAwsInfrastructure.builder()
                          .connectorRef(ParameterField.createValueField("awsconnector"))
                          .credentialsRef(ParameterField.createValueField("sshkey"))
                          .region(ParameterField.createValueField("us-east-2"))
                          .awsInstanceFilter(AwsInstanceFilter.builder().vpcs(List.of("vpc1")).build())
                          .hostConnectionType(ParameterField.createValueField(HostConnectionTypeKind.PRIVATE_IP))
                          .build())
                .build())
        .build();
  }

  private void mockSaveAndGetInstancesOutcomeForTaskStep() {
    doReturn(getInstancesOutcome())
        .when(instanceOutcomeHelper)
        .saveAndGetInstancesOutcome(
            eq(ambiance), any(InfrastructureOutcome.class), any(DelegateResponseData.class), any(Set.class));
  }

  private void mockSaveAndGetInstancesOutcomeForNonTaskStep() {
    doReturn(getInstancesOutcome())
        .when(instanceOutcomeHelper)
        .saveAndGetInstancesOutcome(eq(ambiance), any(InfrastructureOutcome.class), any(Set.class));
  }

  private InstancesOutcome getInstancesOutcome() {
    return InstancesOutcome.builder()
        .instances(List.of(InstanceOutcome.builder().name("instanceName").hostName("instanceHostname").build()))
        .build();
  }
}
