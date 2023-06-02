/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.common.steps.stepgroup;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.NGCommonUtilitiesTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingGrpcOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class StepGroupStepTest extends NGCommonUtilitiesTestBase {
  @Mock ExecutionSweepingGrpcOutputService executionSweepingGrpcOutputService;
  @Inject @InjectMocks private StepGroupStep stepGroupStep;

  private static final String CHILD_ID = generateUuid();

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestObtainChildren() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepGroupStepParameters stateParameters = StepGroupStepParameters.builder().childNodeID(CHILD_ID).build();
    ChildExecutableResponse childExecutableResponse =
        stepGroupStep.obtainChild(ambiance, stateParameters, inputPackage);
    assertThat(childExecutableResponse).isNotNull();
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo(CHILD_ID);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestHandleChildResponse() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    StepGroupStepParameters stateParameters = StepGroupStepParameters.builder().childNodeID(CHILD_ID).build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put(CHILD_ID, StepResponseNotifyData.builder().status(Status.FAILED).build())
            .build();
    StepResponse stepResponse = stepGroupStep.handleChildResponse(ambiance, stateParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(stepGroupStep.getStepParametersClass()).isEqualTo(StepGroupStepParameters.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testStepGroupParameterTest() {
    StepGroupStepParameters stepGroupStepParameters = StepGroupStepParameters.getStepParameters(null, CHILD_ID);
    assertThat(stepGroupStepParameters.getChildNodeID()).isEqualTo(CHILD_ID);

    StepGroupElementConfig stepGroupElementConfig =
        StepGroupElementConfig.builder()
            .name("name")
            .identifier("identifier")
            .skipCondition(ParameterField.createValueField("skipConfition"))
            .failureStrategies(
                ParameterField.createValueField(Collections.singletonList(FailureStrategyConfig.builder().build())))
            .when(ParameterField.createValueField(StepWhenCondition.builder().build()))
            .build();
    stepGroupStepParameters = StepGroupStepParameters.getStepParameters(stepGroupElementConfig, CHILD_ID);
    assertThat(stepGroupStepParameters.getName()).isEqualTo("name");
    assertThat(stepGroupStepParameters.getIdentifier()).isEqualTo("identifier");
    assertThat(stepGroupStepParameters.getChildNodeID()).isEqualTo(CHILD_ID);
    assertThat(stepGroupStepParameters.getSkipCondition().getValue()).isEqualTo("skipConfition");
    assertThat(stepGroupStepParameters.getFailureStrategies()).isNotEmpty();
    assertThat(stepGroupStepParameters.getWhen()).isNotNull();
  }
}
