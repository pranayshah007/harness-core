/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsBlueGreenCreateServiceDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.elastigroup.ElastigroupFixedInstances;
import io.harness.cdng.elastigroup.ElastigroupInstances;
import io.harness.cdng.elastigroup.ElastigroupInstancesType;
import io.harness.cdng.elastigroup.ElastigroupSetupStep;
import io.harness.cdng.elastigroup.ElastigroupSetupStepParameters;
import io.harness.cdng.elastigroup.ElastigroupStepCommonHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.wings.beans.TaskType;

public class ElastigroupSetupStepTest extends CDNGTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Mock private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private ElastigroupSetupStep elastigroupSetupStep;

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void executeElastigroupTaskTest() {
    Ambiance ambiance = Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
            .build();
    String elastigroupJson = "elastigroupJson";
    ElastigroupFixedInstances elastigroupFixedInstances = ElastigroupFixedInstances.builder().desired(ParameterField.<Integer>builder().value(1).build())
            .min(ParameterField.<Integer>builder().value(1).build())
            .max(ParameterField.<Integer>builder().value(1).build())
            .build();
    ElastigroupInstances elastigroupInstances = ElastigroupInstances.builder().spec(elastigroupFixedInstances).type(ElastigroupInstancesType.FIXED).build();
    String elastigroupNamePrefix = "elastigroupNamePrefix";
    String startupScript = "startupScript";
    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
            ElastigroupSetupStepParameters.infoBuilder()
                    .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
                    .instances(elastigroupInstances)
                    .build();
    StepElementParameters stepElementParameters =
            StepElementParameters.builder().spec(elastigroupSetupStepParameters).timeout(ParameterField.createValueField("10m")).build();
    String ELASTIGROUP_SETUP_COMMAND_NAME = "ElastigroupSetup";
    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
            .elastigroupParameters(elastigroupJson)
            .startupScript(startupScript)
            .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome = ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData = ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupInfrastructureOutcome).build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().build();
    doReturn(spotInstConfig).when(elastigroupStepCommonHelper).getSpotInstConfig(elastigroupInfrastructureOutcome, ambiance);
    ElastiGroup elastiGroup = ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build();
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).generateConfigFromJson(elastigroupJson);
    doReturn(startupScript).when(elastigroupStepCommonHelper).getBase64EncodedStartupScript(ambiance, startupScript);
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
            ElastigroupSetupCommandRequest.builder()
                    .blueGreen(false)
                    .elastigroupNamePrefix(elastigroupNamePrefix)
                    .accountId("test-account")
                    .spotInstConfig(spotInstConfig)
                    .elastigroupJson(elastigroupStepExecutorParams.getElastigroupParameters())
                    .startupScript(startupScript)
                    .commandName(ELASTIGROUP_SETUP_COMMAND_NAME)
                    .image(elastigroupStepExecutorParams.getImage())
                    .commandUnitsProgress(null)
                    .timeoutIntervalInMin(10)
                    .maxInstanceCount(1)
                    .currentRunningInstanceCount(
                            null)
                    .useCurrentRunningInstanceCount(false)
                    .elastigroupOriginalConfig(elastiGroup)
                    .build();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
            .chainEnd(false)
            .taskRequest(TaskRequest.newBuilder().build())
            .passThroughData(elastigroupExecutionPassThroughData)
            .build();
    doReturn(taskChainResponse).when(elastigroupStepCommonHelper).queueElastigroupTask(stepElementParameters, elastigroupSetupCommandRequest, ambiance, elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG);
    elastigroupSetupStep.executeElastigroupTask(
            ambiance, stepElementParameters, elastigroupExecutionPassThroughData, null, elastigroupStepExecutorParams);
//    verify(elastigroupStepCommonHelper)
//            .queueElastigroupTask(
//                    stepElementParameters, elastigroupSetupCommandRequest, ambiance, elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextTest() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
            .build();
    String elastigroupJson = "elastigroupJson";
    ElastigroupFixedInstances elastigroupFixedInstances = ElastigroupFixedInstances.builder().desired(ParameterField.<Integer>builder().value(1).build())
            .min(ParameterField.<Integer>builder().value(1).build())
            .max(ParameterField.<Integer>builder().value(1).build())
            .build();
    ElastigroupInstances elastigroupInstances = ElastigroupInstances.builder().spec(elastigroupFixedInstances).type(ElastigroupInstancesType.FIXED).build();
    String elastigroupNamePrefix = "elastigroupNamePrefix";
    String startupScript = "startupScript";
    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
            ElastigroupSetupStepParameters.infoBuilder()
                    .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
                    .instances(elastigroupInstances)
                    .build();
    StepElementParameters stepElementParameters =
            StepElementParameters.builder().spec(elastigroupSetupStepParameters).timeout(ParameterField.createValueField("10m")).build();
    ElastiGroup elastiGroup = ElastiGroup.builder().name(elastigroupNamePrefix).id("123").capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build();
    ElastigroupSetupResult elastigroupSetupResult = ElastigroupSetupResult.builder().elastiGroupNamePrefix(elastigroupNamePrefix)
            .currentRunningInstanceCount(2)
            .useCurrentRunningInstanceCount(false)
            .newElastiGroup(elastiGroup)
            .elastigroupOriginalConfig(elastiGroup)
            .maxInstanceCount(1)
            .groupToBeDownsized(Arrays.asList(elastiGroup))
            .build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    ElastigroupSetupResponse elastigroupSetupResponse = ElastigroupSetupResponse.builder()
            .elastigroupSetupResult(elastigroupSetupResult)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome = ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData = ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupInfrastructureOutcome).build();
    ResponseData responseData = elastigroupSetupResponse;
    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;
    StepResponse.StepResponseBuilder stepResponseBuilder =
            StepResponse.builder().unitProgressList(elastigroupSetupResponse.getUnitProgressData().getUnitProgresses());
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).fetchOldElasticGroup(elastigroupSetupResult);
    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
            ElastigroupSetupDataOutcome.builder()
                    .resizeStrategy(elastigroupSetupResult.getResizeStrategy())
                    .elastigroupNamePrefix(elastigroupSetupResult.getElastiGroupNamePrefix())
                    .useCurrentRunningInstanceCount(elastigroupSetupResult.isUseCurrentRunningInstanceCount())
                    .currentRunningInstanceCount(1)
                    .maxInstanceCount(elastigroupSetupResult.getMaxInstanceCount())
                    .isBlueGreen(elastigroupSetupResult.isBlueGreen())
                    .oldElastigroupOriginalConfig(elastiGroup)
                    .newElastigroupOriginalConfig(elastigroupSetupResult.getElastigroupOriginalConfig())
                    .build();
    StepResponse stepResponse = elastigroupSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters, elastigroupExecutionPassThroughData, responseSupplier);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    assertThat(((ElastigroupSetupDataOutcome) stepResponse.getStepOutcomes().stream().findFirst().get().getOutcome()).getElastigroupNamePrefix()).isEqualTo(elastigroupNamePrefix);
  }
}
