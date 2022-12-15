/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.elastigroup.output.ElastigroupConfigurationOutput;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
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
import io.harness.exception.InvalidRequestException;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
  @Spy private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @InjectMocks private ElastigroupStepCommonHelper elastigroupStepCommonHelper;

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void renderCountTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
    int value = elastigroupStepCommonHelper.renderCount(ParameterField.<Integer>builder().build(), 2, ambiance);
    assertThat(value).isEqualTo(2);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void getInfrastructureOutcomeTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
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

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void startChainLinkTest() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();

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
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().build();

    elastigroupStepCommonHelper.startChainLink(ambiance, stepElementParameters, elastigroupExecutionPassThroughData);
  }
}
