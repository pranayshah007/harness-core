/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.deploy;

import static io.harness.rule.OwnerRule.FILIP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpecType;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.cdng.common.capacity.PercentageCapacitySpec;
import io.harness.cdng.elastigroup.ElastigroupEntityHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskParameters;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDeployStepHelperTest extends CategoryTest {
  @Mock ElastigroupEntityHelper entityHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock ILogStreamingStepClient logStreamingStepClient;
  @Mock OutcomeService outcomeService;

  @InjectMocks ElastigroupDeployStepHelper stepHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testHandleTaskFailure() throws Exception {
    // Given
    Ambiance ambiance = mock(Ambiance.class);

    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .spec(ElastigroupDeployStepParameters.builder().build())
                                               .timeout(ParameterField.createValueField("15m"))
                                               .build();

    // When
    StepResponse response =
        stepHelper.handleTaskFailure(ambiance, stepParameters, new InvalidArgumentsException("Test message"));

    // Then
    assertThat(response).isNotNull().extracting(StepResponse::getStatus).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testHandleTaskResult() throws Exception {
    // Given
    Ambiance ambiance = mock(Ambiance.class);
    StepOutcome stepOutcome = mock(StepOutcome.class);

    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .spec(ElastigroupDeployStepParameters.builder().build())
                                               .timeout(ParameterField.createValueField("15m"))
                                               .build();

    ElastigroupDeployTaskResponse taskResponse = ElastigroupDeployTaskResponse.builder()
                                                     .status(CommandExecutionStatus.SUCCESS)
                                                     .ec2InstanceIdsAdded(Collections.emptyList())
                                                     .ec2InstanceIdsExisting(Collections.emptyList())
                                                     .build();

    // When
    StepResponse stepResponse = stepHelper.handleTaskResult(ambiance, stepParameters, taskResponse, stepOutcome);

    // Then
    assertThat(stepResponse)
        .isNotNull()
        .extracting(StepResponse::getStatus, StepResponse::getStepOutcomes)
        .containsExactly(Status.SUCCEEDED, Collections.singletonList(stepOutcome));
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetElastigroupDeployTaskParametersCountFinal() {
    // Given
    Ambiance ambiance = anAmbiance();

    ElastigroupDeployStepParameters stepParameters =
        ElastigroupDeployStepParameters.builder()
            .newService(Capacity.builder()
                            .type(CapacitySpecType.COUNT)
                            .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(200)).build())
                            .build())
            .oldService(Capacity.builder().build())
            .build();

    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(ElastigroupInfrastructureOutcome.builder().connectorRef("ConnectorDisconnectHandler").build())
                        .build());

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(ElastigroupSetupDataOutcome.builder()
                            .newElastigroupOriginalConfig(
                                ElastiGroup.builder()
                                    .capacity(ElastiGroupCapacity.builder().minimum(10).target(20).maximum(30).build())
                                    .build())
                            .oldElastigroupOriginalConfig(
                                ElastiGroup.builder()
                                    .capacity(ElastiGroupCapacity.builder().minimum(1).target(2).maximum(3).build())
                                    .build())
                            .build())
                .build());

    when(entityHelper.getConnectorInfoDTO(anyString(), any()))
        .thenReturn(ConnectorInfoDTO.builder().connectorConfig(SpotConnectorDTO.builder().build()).build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    // When
    ElastigroupDeployTaskParameters taskParameters =
        stepHelper.getElastigroupDeployTaskParameters(stepParameters, ambiance, stepElementParameters);

    // Then
    assertThat(taskParameters)
        .isNotNull()
        .extracting(
            ElastigroupDeployTaskParameters::getNewElastigroup, ElastigroupDeployTaskParameters::getOldElastigroup)
        .containsExactly(ElastiGroup.builder()
                             .capacity(ElastiGroupCapacity.builder().minimum(10).target(20).maximum(30).build())
                             .build(),
            ElastiGroup.builder()
                .capacity(ElastiGroupCapacity.builder().minimum(0).target(0).maximum(0).build())
                .build());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetElastigroupDeployTaskParametersPercentageNotFinal() {
    // Given
    Ambiance ambiance = anAmbiance();

    ElastigroupDeployStepParameters stepParameters =
        ElastigroupDeployStepParameters.builder()
            .newService(
                Capacity.builder()
                    .type(CapacitySpecType.PERCENTAGE)
                    .spec(PercentageCapacitySpec.builder().percentage(ParameterField.createValueField(50)).build())
                    .build())
            .oldService(
                Capacity.builder()
                    .type(CapacitySpecType.PERCENTAGE)
                    .spec(PercentageCapacitySpec.builder().percentage(ParameterField.createValueField(100)).build())
                    .build())
            .build();

    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(ElastigroupInfrastructureOutcome.builder().connectorRef("ConnectorDisconnectHandler").build())
                        .build());

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(ElastigroupSetupDataOutcome.builder()
                            .newElastigroupOriginalConfig(
                                ElastiGroup.builder()
                                    .capacity(ElastiGroupCapacity.builder().minimum(10).target(20).maximum(30).build())
                                    .build())
                            .oldElastigroupOriginalConfig(
                                ElastiGroup.builder()
                                    .capacity(ElastiGroupCapacity.builder().minimum(1).target(2).maximum(3).build())
                                    .build())
                            .build())
                .build());

    when(entityHelper.getConnectorInfoDTO(anyString(), any()))
        .thenReturn(ConnectorInfoDTO.builder().connectorConfig(SpotConnectorDTO.builder().build()).build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    // When
    ElastigroupDeployTaskParameters taskParameters =
        stepHelper.getElastigroupDeployTaskParameters(stepParameters, ambiance, stepElementParameters);

    // Then
    assertThat(taskParameters)
        .isNotNull()
        .extracting(
            ElastigroupDeployTaskParameters::getNewElastigroup, ElastigroupDeployTaskParameters::getOldElastigroup)
        .containsExactly(ElastiGroup.builder()
                             .capacity(ElastiGroupCapacity.builder().minimum(10).target(10).maximum(10).build())
                             .build(),
            ElastiGroup.builder()
                .capacity(ElastiGroupCapacity.builder().minimum(2).target(2).maximum(2).build())
                .build());
  }

  private Ambiance anAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
        .build();
  }
}
