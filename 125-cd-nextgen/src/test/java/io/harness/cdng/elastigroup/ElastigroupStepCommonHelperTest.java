/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.elastigroup.output.ElastigroupConfigurationOutput;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ElastigroupStepCommonHelperTest extends CDNGTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ElastigroupEntityHelper elastigroupEntityHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock protected OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ElastigroupStepExecutor elastigroupStepExecutor;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient logStreamingStepClient;
  @Mock private FileStoreService fileStoreService;

  @InjectMocks private ElastigroupStepCommonHelper elastigroupStepCommonHelper;

  @Before
  public void setUp() throws Exception {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void renderCountTest() {
    Ambiance ambiance = anAmbiance();
    int value = elastigroupStepCommonHelper.renderCount(ParameterField.<Integer>builder().build(), 2, ambiance);
    assertThat(value).isEqualTo(2);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void getInfrastructureOutcomeTest() {
    Ambiance ambiance = anAmbiance();
    InfrastructureOutcome infrastructureOutcome = ElastigroupInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    assertThat(elastigroupStepCommonHelper.getInfrastructureOutcome(ambiance)).isEqualTo(infrastructureOutcome);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void renderExpressionTest() {
    Ambiance ambiance = anAmbiance();
    String stringObject = "str";
    doReturn(stringObject).when(engineExpressionService).renderExpression(ambiance, stringObject);
    assertThat(elastigroupStepCommonHelper.renderExpression(ambiance, stringObject)).isEqualTo(stringObject);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void fetchOldElasticGroupTest() {
    ElastiGroup elastiGroup = ElastiGroup.builder().build();
    ElastigroupSetupResult elastigroupSetupResult =
        ElastigroupSetupResult.builder().groupToBeDownsized(Arrays.asList(elastiGroup)).build();
    assertThat(elastigroupStepCommonHelper.fetchOldElasticGroup(elastigroupSetupResult)).isEqualTo(elastiGroup);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void executeNextLinkTest() throws Exception {
    Ambiance ambiance = anAmbiance();
    String startupScript = "startupScript";
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse =
        ElastigroupStartupScriptFetchResponse.builder()
            .unitProgressData(unitProgressData)
            .taskStatus(TaskStatus.SUCCESS)
            .build();
    ResponseData responseData = elastigroupStartupScriptFetchResponse;
    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;

    ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData =
        ElastigroupStartupScriptFetchPassThroughData.builder().startupScript(startupScript).build();
    InfrastructureOutcome infrastructureOutcome = ElastigroupInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    String value = "value";
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.<String>builder().value(value).build()).build();
    ElastigroupConfigurationOutput elastigroupConfigurationOutput =
        ElastigroupConfigurationOutput.builder().storeConfig(storeConfig).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(elastigroupConfigurationOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_CONFIGURATION_OUTPUT));
    doReturn(value).when(engineExpressionService).renderExpression(ambiance, value);
    //            assertThat(elastigroupStepCommonHelper.renderExpression(ambiance,
    //            stringObject)).isEqualTo(stringObject);

    String amiId = "amiId";
    AMIArtifactOutcome amiArtifactOutcome = AMIArtifactOutcome.builder().amiId(amiId).build();
    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(amiArtifactOutcome).build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(Boolean.TRUE).outcome(artifactsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));

    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .shouldOpenFetchFilesLogStream(false)
                                                                      .startupScript(startupScript)
                                                                      .image(amiId)
                                                                      .elastigroupConfiguration(value)
                                                                      .build();

    elastigroupStepCommonHelper.executeNextLink(elastigroupStepExecutor, ambiance, stepElementParameters,
        elastigroupStartupScriptFetchPassThroughData, responseSupplier);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void addLoadBalancerConfigAfterExpressionEvaluationTest() {
    Ambiance ambiance = anAmbiance();
    AwsLoadBalancerConfigYaml awsLoadBalancerConfigYaml =
        AwsLoadBalancerConfigYaml.builder()
            .loadBalancer(ParameterField.<String>builder().value("a").build())
            .prodListenerPort(ParameterField.<String>builder().value("b").build())
            .stageListenerPort(ParameterField.<String>builder().value("c").build())
            .prodListenerRuleArn(ParameterField.<String>builder().value("d").build())
            .stageListenerRuleArn(ParameterField.<String>builder().value("e").build())
            .build();
    LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment = LoadBalancerDetailsForBGDeployment.builder()
                                                                                .loadBalancerName("a")
                                                                                .prodListenerPort("b")
                                                                                .stageListenerPort("c")
                                                                                .prodRuleArn("d")
                                                                                .stageRuleArn("e")
                                                                                .useSpecificRules(true)
                                                                                .build();
    doReturn("a").when(engineExpressionService).renderExpression(ambiance, "a");
    doReturn("b").when(engineExpressionService).renderExpression(ambiance, "b");
    doReturn("c").when(engineExpressionService).renderExpression(ambiance, "c");
    doReturn("d").when(engineExpressionService).renderExpression(ambiance, "d");
    doReturn("e").when(engineExpressionService).renderExpression(ambiance, "e");

    assertThat(elastigroupStepCommonHelper.addLoadBalancerConfigAfterExpressionEvaluation(
                   Arrays.asList(awsLoadBalancerConfigYaml), ambiance))
        .isEqualTo(Arrays.asList(loadBalancerDetailsForBGDeployment));
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void handleTaskExceptionTest() throws Exception {
    Ambiance ambiance = anAmbiance();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().build();
    String message = "msg";
    Exception e = new NullPointerException(message);
    StepResponse stepResponse =
        elastigroupStepCommonHelper.handleTaskException(ambiance, elastigroupExecutionPassThroughData, e);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).contains(e.getMessage());
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void handleElastigroupParametersTaskFailureTest() throws Exception {
    String message = "msg";
    ElastigroupParametersFetchFailurePassThroughData elastigroupExecutionPassThroughData =
        ElastigroupParametersFetchFailurePassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().build())
            .errorMsg(message)
            .build();
    StepResponse stepResponse =
        elastigroupStepCommonHelper.handleElastigroupParametersTaskFailure(elastigroupExecutionPassThroughData);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).contains(message);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void handleStartupScriptTaskFailureTest() throws Exception {
    String message = "msg";
    ElastigroupStartupScriptFetchFailurePassThroughData elastigroupExecutionPassThroughData =
        ElastigroupStartupScriptFetchFailurePassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().build())
            .errorMsg(message)
            .build();
    StepResponse stepResponse =
        elastigroupStepCommonHelper.handleStartupScriptTaskFailure(elastigroupExecutionPassThroughData);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).contains(message);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void queueElastigroupTaskTest() throws Exception {
    Ambiance ambiance = anAmbiance();
    SpotCredentialDTO spotCredentialDTO = SpotCredentialDTO.builder().build();
    SpotConnectorDTO spotConnectorDTO = SpotConnectorDTO.builder().credential(spotCredentialDTO).build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().spotConnectorDTO(spotConnectorDTO).build();
    TaskType taskType = TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG;
    ElastigroupSpecParameters elastigroupSpecParameters = ElastigroupSetupStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(elastigroupSpecParameters).build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().build();
    ElastigroupCommandRequest elastigroupCommandRequest =
        ElastigroupSetupCommandRequest.builder().spotInstConfig(spotInstConfig).build();
    String message = "msg";
    Exception e = new NullPointerException(message);
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.queueElastigroupTask(stepElementParameters,
        elastigroupCommandRequest, ambiance, elastigroupExecutionPassThroughData, false, taskType);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(elastigroupExecutionPassThroughData);
  }

  //  @Test(expected = InvalidRequestException.class)
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void startChainLinkTest() throws Exception {
    Ambiance ambiance = anAmbiance();

    String startupScript = "startupScript";
    doReturn(startupScript).when(engineExpressionService).renderExpression(ambiance, startupScript);
    StoreConfig storeConfig1 =
        InlineStoreConfig.builder().content(ParameterField.<String>builder().value(startupScript).build()).build();
    StartupScriptOutcome startupScriptOutcome = StartupScriptOutcome.builder().store(storeConfig1).build();
    OptionalOutcome optionalOutcome1 = OptionalOutcome.builder().outcome(startupScriptOutcome).build();
    doReturn(optionalOutcome1)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    String value = "value";
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.<String>builder().value(value).build()).build();
    ElastigroupConfigurationOutput elastigroupConfigurationOutput =
        ElastigroupConfigurationOutput.builder().storeConfig(storeConfig).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(elastigroupConfigurationOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_CONFIGURATION_OUTPUT));
    doReturn(value).when(engineExpressionService).renderExpression(ambiance, value);

    String amiId = "amiId";
    AMIArtifactOutcome amiArtifactOutcome = AMIArtifactOutcome.builder().amiId(amiId).build();
    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(amiArtifactOutcome).build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(Boolean.TRUE).outcome(artifactsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));

    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .shouldOpenFetchFilesLogStream(false)
                                                                      .startupScript(startupScript)
                                                                      .image(amiId)
                                                                      .elastigroupConfiguration(value)
                                                                      .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    //    elastigroupStepCommonHelper.startChainLink(ambiance, stepElementParameters, passThroughData);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void startChainLinkNoStartupScriptAndNoConfigurationTest() {
    // given
    Ambiance ambiance = anAmbiance();

    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().build());

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().build());

    // when
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.startChainLink(ambiance, null, null);

    // then
    assertThat(taskChainResponse.isChainEnd()).isTrue();

    assertThat(taskChainResponse.getPassThroughData())
        .isNotNull()
        .isInstanceOf(ElastigroupStepExceptionPassThroughData.class);

    assertThat(((ElastigroupStepExceptionPassThroughData) taskChainResponse.getPassThroughData()).getErrorMessage())
        .isEqualTo("Elastigroup Configuration provided is empty");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void startChainLinkUnsupportedStoreTypeTest() {
    // given
    Ambiance ambiance = anAmbiance();

    StartupScriptOutcome startupScriptOutcome =
        StartupScriptOutcome.builder().store(InlineStoreConfig.builder().build()).build();

    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().outcome(startupScriptOutcome).found(true).build());

    // when
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.startChainLink(ambiance, null, null);

    // then
    assertThat(taskChainResponse.isChainEnd()).isTrue();

    assertThat(taskChainResponse.getPassThroughData())
        .isNotNull()
        .isInstanceOf(ElastigroupStepExceptionPassThroughData.class);

    assertThat(((ElastigroupStepExceptionPassThroughData) taskChainResponse.getPassThroughData()).getErrorMessage())
        .isEqualTo("Store Type provided for Startup Script Not Supported");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void startChainLinkUnsupportedStoreTypeForConfigTest() {
    // given
    Ambiance ambiance = anAmbiance();

    StartupScriptOutcome startupScriptOutcome =
        StartupScriptOutcome.builder()
            .store(HarnessStore.builder()
                       .files(ParameterField.createValueField(Collections.singletonList("file1")))
                       .build())
            .build();

    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().outcome(startupScriptOutcome).found(true).build());

    when(engineExpressionService.renderExpression(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class));

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(FileNodeDTO.builder().content("Startup script content").build()));

    ElastigroupConfigurationOutput elastigroupConfigurationOutput =
        ElastigroupConfigurationOutput.builder().storeConfig(InlineStoreConfig.builder().build()).build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().output(elastigroupConfigurationOutput).found(true).build());

    // when
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.startChainLink(
        ambiance, null, ElastigroupExecutionPassThroughData.builder().build());

    // then
    assertThat(taskChainResponse.isChainEnd()).isTrue();

    assertThat(taskChainResponse.getPassThroughData())
        .isNotNull()
        .isInstanceOf(ElastigroupStepExceptionPassThroughData.class);

    assertThat(((ElastigroupStepExceptionPassThroughData) taskChainResponse.getPassThroughData()).getErrorMessage())
        .isEqualTo("Store Type provided for Elastigroup Configuration is not supported");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void startChainLinkNoArtifactTest() {
    // given
    Ambiance ambiance = anAmbiance();

    StartupScriptOutcome startupScriptOutcome =
        StartupScriptOutcome.builder()
            .store(HarnessStore.builder()
                       .files(ParameterField.createValueField(Collections.singletonList("script1")))
                       .build())
            .build();

    when(outcomeService.resolveOptional(
             any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT))))
        .thenReturn(OptionalOutcome.builder().outcome(startupScriptOutcome).found(true).build());

    when(engineExpressionService.renderExpression(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class));

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), eq("script1"), anyBoolean()))
        .thenReturn(Optional.of(FileNodeDTO.builder().content("Startup script content").build()));

    ElastigroupConfigurationOutput elastigroupConfigurationOutput =
        ElastigroupConfigurationOutput.builder()
            .storeConfig(HarnessStore.builder()
                             .files(ParameterField.createValueField(Collections.singletonList("config1")))
                             .build())
            .build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().output(elastigroupConfigurationOutput).found(true).build());

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), eq("config1"), anyBoolean()))
        .thenReturn(Optional.of(FileNodeDTO.builder().content("Config content").build()));

    when(outcomeService.resolveOptional(
             any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS))))
        .thenReturn(OptionalOutcome.builder().build());

    // when
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.startChainLink(
        ambiance, null, ElastigroupExecutionPassThroughData.builder().build());

    // then
    assertThat(taskChainResponse.isChainEnd()).isTrue();

    assertThat(taskChainResponse.getPassThroughData())
        .isNotNull()
        .isInstanceOf(ElastigroupStepExceptionPassThroughData.class);

    assertThat(((ElastigroupStepExceptionPassThroughData) taskChainResponse.getPassThroughData()).getErrorMessage())
        .isEqualTo("AMI not available. Please specify the AMI artifact");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void startChainLinkPositiveTest() {
    // given
    Ambiance ambiance = anAmbiance();

    StartupScriptOutcome startupScriptOutcome =
        StartupScriptOutcome.builder()
            .store(HarnessStore.builder()
                       .files(ParameterField.createValueField(Collections.singletonList("script1")))
                       .build())
            .build();

    when(outcomeService.resolveOptional(
             any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT))))
        .thenReturn(OptionalOutcome.builder().outcome(startupScriptOutcome).found(true).build());

    when(engineExpressionService.renderExpression(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class));

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), eq("script1"), anyBoolean()))
        .thenReturn(Optional.of(FileNodeDTO.builder().content("Startup script content").build()));

    ElastigroupConfigurationOutput elastigroupConfigurationOutput =
        ElastigroupConfigurationOutput.builder()
            .storeConfig(HarnessStore.builder()
                             .files(ParameterField.createValueField(Collections.singletonList("config1")))
                             .build())
            .build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().output(elastigroupConfigurationOutput).found(true).build());

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), eq("config1"), anyBoolean()))
        .thenReturn(Optional.of(FileNodeDTO.builder().content("Config content").build()));

    when(outcomeService.resolveOptional(
             any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS))))
        .thenReturn(
            OptionalOutcome.builder()
                .outcome(
                    ArtifactsOutcome.builder().primary(AMIArtifactOutcome.builder().amiId("amiId").build()).build())
                .found(true)
                .build());

    when(elastigroupEntityHelper.getSpotInstConfig(any(), any()))
        .thenReturn(
            SpotInstConfig.builder()
                .spotConnectorDTO(SpotConnectorDTO.builder().credential(SpotCredentialDTO.builder().build()).build())
                .build());

    // when
    TaskChainResponse taskChainResponse = elastigroupStepCommonHelper.startChainLink(ambiance,
        StepElementParameters.builder()
            .spec(ElastigroupSetupStepParameters.infoBuilder().name(ParameterField.createValueField("name")).build())
            .build(),
        ElastigroupExecutionPassThroughData.builder().build());

    // then
    assertThat(taskChainResponse.isChainEnd()).isFalse();

    assertThat(taskChainResponse.getPassThroughData())
        .isNotNull()
        .isInstanceOf(ElastigroupExecutionPassThroughData.class);

    assertThat(
        ((ElastigroupExecutionPassThroughData) taskChainResponse.getPassThroughData()).getElastigroupConfiguration())
        .isEqualTo("Config content");
  }

  private Ambiance anAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
        .build();
  }
}
