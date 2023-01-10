/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.FILIP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStepHelper;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStepParameters;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

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
}
