/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

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
    Ambiance ambiance = anAmbiance();
    String elastigroupJson = "elastigroupJson";
    ElastigroupFixedInstances elastigroupFixedInstances =
        ElastigroupFixedInstances.builder()
            .desired(ParameterField.<Integer>builder().value(1).build())
            .min(ParameterField.<Integer>builder().value(1).build())
            .max(ParameterField.<Integer>builder().value(1).build())
            .build();
    ElastigroupInstances elastigroupInstances =
        ElastigroupInstances.builder().spec(elastigroupFixedInstances).type(ElastigroupInstancesType.FIXED).build();
    String elastigroupNamePrefix = "elastigroupNamePrefix";
    String startupScript = "startupScript";
    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
        ElastigroupSetupStepParameters.infoBuilder()
            .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
            .instances(elastigroupInstances)
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupSetupStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    String ELASTIGROUP_SETUP_COMMAND_NAME = "ElastigroupSetup";
    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .elastigroupConfiguration(elastigroupJson)
                                                                      .startupScript(startupScript)
                                                                      .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupInfrastructureOutcome)
            .elastigroupConfiguration(elastigroupJson)
            .build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().build();
    doReturn(spotInstConfig)
        .when(elastigroupStepCommonHelper)
        .getSpotInstConfig(elastigroupInfrastructureOutcome, ambiance);
    ElastiGroup elastiGroup =
        ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build();
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).generateConfigFromJson(elastigroupJson);
    doReturn(startupScript).when(elastigroupStepCommonHelper).getBase64EncodedStartupScript(ambiance, startupScript);
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
        ElastigroupSetupCommandRequest.builder()
            .blueGreen(false)
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .accountId("test-account")
            .spotInstConfig(spotInstConfig)
            .elastigroupConfiguration(elastigroupStepExecutorParams.getElastigroupConfiguration())
            .startupScript(startupScript)
            .commandName(ELASTIGROUP_SETUP_COMMAND_NAME)
            .image(elastigroupStepExecutorParams.getImage())
            .commandUnitsProgress(null)
            .timeoutIntervalInMin(10)
            .maxInstanceCount(1)
            .useCurrentRunningInstanceCount(false)
            .generatedElastigroupConfig(elastiGroup)
            .build();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(elastigroupExecutionPassThroughData)
                                              .build();
    doReturn(taskChainResponse)
        .when(elastigroupStepCommonHelper)
        .queueElastigroupTask(stepElementParameters, elastigroupSetupCommandRequest, ambiance,
            elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG);
    elastigroupSetupStep.executeElastigroupTask(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, null);
    //    verify(elastigroupStepCommonHelper)
    //            .queueElastigroupTask(
    //                    stepElementParameters, elastigroupSetupCommandRequest, ambiance,
    //                    elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextTest() throws Exception {
    Ambiance ambiance = anAmbiance();
    String elastigroupJson = "elastigroupJson";
    ElastigroupFixedInstances elastigroupFixedInstances =
        ElastigroupFixedInstances.builder()
            .desired(ParameterField.<Integer>builder().value(1).build())
            .min(ParameterField.<Integer>builder().value(1).build())
            .max(ParameterField.<Integer>builder().value(1).build())
            .build();
    ElastigroupInstances elastigroupInstances =
        ElastigroupInstances.builder().spec(elastigroupFixedInstances).type(ElastigroupInstancesType.FIXED).build();
    String elastigroupNamePrefix = "elastigroupNamePrefix";
    String startupScript = "startupScript";
    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
        ElastigroupSetupStepParameters.infoBuilder()
            .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
            .instances(elastigroupInstances)
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupSetupStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    ElastiGroup elastiGroup = ElastiGroup.builder()
                                  .name(elastigroupNamePrefix)
                                  .id("123")
                                  .capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build())
                                  .build();
    ElastigroupSetupResult elastigroupSetupResult = ElastigroupSetupResult.builder()
                                                        .elastigroupNamePrefix(elastigroupNamePrefix)
                                                        .useCurrentRunningInstanceCount(false)
                                                        .newElastigroup(elastiGroup)
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
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().infrastructure(elastigroupInfrastructureOutcome).build();
    ResponseData responseData = elastigroupSetupResponse;
    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).fetchOldElasticGroup(elastigroupSetupResult);
    StepResponse stepResponse = elastigroupSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, responseSupplier);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    assertThat(((ElastigroupSetupDataOutcome) stepResponse.getStepOutcomes().stream().findFirst().get().getOutcome())
                   .getElastigroupNamePrefix())
        .isEqualTo(elastigroupNamePrefix);
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void executeElastigroupTaskQueueTaskCurrentRunningTest() throws Exception {
    // Given
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(ElastigroupSetupStepParameters.infoBuilder()
                      .name(ParameterField.createValueField("name"))
                      .instances(ElastigroupInstances.builder().type(ElastigroupInstancesType.CURRENT_RUNNING).build())
                      .build())
            .build();
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder().elastigroupConfiguration("{dummy-json:config}").build();

    when(elastigroupStepCommonHelper.generateConfigFromJson(any()))
        .thenReturn(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build());

    // When
    elastigroupSetupStep.executeElastigroupTask(
        anAmbiance(), stepElementParameters, passThroughData, UnitProgressData.builder().build());

    // Then
    verify(elastigroupStepCommonHelper)
        .queueElastigroupTask(eq(stepElementParameters), any(), eq(anAmbiance()), eq(passThroughData), eq(true),
            eq(TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG));
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void executeElastigroupTaskQueueTaskFixedTest() throws Exception {
    // Given
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(ElastigroupSetupStepParameters.infoBuilder()
                      .name(ParameterField.createValueField("name"))
                      .instances(ElastigroupInstances.builder()
                                     .type(ElastigroupInstancesType.FIXED)
                                     .spec(ElastigroupFixedInstances.builder()
                                               .min(ParameterField.createValueField(1))
                                               .desired(ParameterField.createValueField(2))
                                               .max(ParameterField.createValueField(4))
                                               .build())
                                     .build())
                      .build())
            .build();
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder().elastigroupConfiguration("{dummy-json:config}").build();

    when(elastigroupStepCommonHelper.generateConfigFromJson(any()))
        .thenReturn(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build());

    // When
    elastigroupSetupStep.executeElastigroupTask(
        anAmbiance(), stepElementParameters, passThroughData, UnitProgressData.builder().build());

    // Then
    verify(elastigroupStepCommonHelper)
        .queueElastigroupTask(eq(stepElementParameters), any(), eq(anAmbiance()), eq(passThroughData), eq(true),
            eq(TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG));
  }

  @Test
  @Owner(developers = {VLICA})
  @Category(UnitTests.class)
  public void executeElasticGroupTaskAndExceptionThrownWhenParsingJson() throws Exception {
    // Given
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(ElastigroupSetupStepParameters.infoBuilder()
                      .name(ParameterField.createValueField("name"))
                      .instances(ElastigroupInstances.builder()
                                     .type(ElastigroupInstancesType.FIXED)
                                     .spec(ElastigroupFixedInstances.builder()
                                               .min(ParameterField.createValueField(1))
                                               .desired(ParameterField.createValueField(2))
                                               .max(ParameterField.createValueField(4))
                                               .build())
                                     .build())
                      .build())
            .build();
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder().elastigroupConfiguration("{dummy-json:config}").build();

    String errorMessage = "End of input at line 123";

    when(elastigroupStepCommonHelper.generateConfigFromJson(any())).thenThrow(new RuntimeException(errorMessage));

    NGLogCallback mockCallback = mock(NGLogCallback.class);
    doReturn(mockCallback).when(elastigroupStepCommonHelper).getLogCallback(anyString(), any(), anyBoolean());

    when(elastigroupStepCommonHelper.stepFailureTaskResponseWithMessage(any(), anyString()))
        .thenReturn(
            TaskChainResponse.builder()
                .chainEnd(true)
                .passThroughData(ElastigroupStepExceptionPassThroughData.builder().errorMessage(errorMessage).build())
                .build());

    // When
    TaskChainResponse taskChainResponse = elastigroupSetupStep.executeElastigroupTask(anAmbiance(),
        stepElementParameters, passThroughData, UnitProgressData.builder().unitProgresses(new ArrayList<>()).build());

    ElastigroupStepExceptionPassThroughData exceptionPassThroughData =
        (ElastigroupStepExceptionPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(exceptionPassThroughData.getErrorMessage()).isEqualTo(errorMessage);
  }

  private Ambiance anAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
        .build();
  }
}
