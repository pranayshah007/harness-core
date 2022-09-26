/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.tasks.ResponseData;
import io.harness.wait.WaitStepInstance;
import io.harness.yaml.core.timeout.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepTest extends OrchestrationStepsTestBase {
  @Mock WaitStepService waitStepService;
  @InjectMocks WaitStep waitStep;
  Ambiance ambiance;
  WaitStepParameters waitStepParameters;
  Timeout duration;
  String nodeExecutionId;
  StepElementParameters stepElementParameters;
  @Before
  public void setup() {
    nodeExecutionId = generateUuid();
    ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build();
    duration = Timeout.builder().timeoutString("10m").build();
    waitStepParameters = WaitStepParameters.infoBuilder().duration(ParameterField.createValueField(duration)).build();
    stepElementParameters = StepElementParameters.builder().spec(waitStepParameters).build();
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    String correlationId = generateUuid();
    WaitStepInstance waitStepInstance = WaitStepInstance.builder().waitStepInstanceId(correlationId).build();
    doReturn(Optional.of(waitStepInstance)).when(waitStepService).findByNodeExecutionId(nodeExecutionId);
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    StepResponse response = waitStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertEquals(response.getStatus(), Status.SUCCEEDED);
    responseDataMap.put(correlationId, WaitStepResponseData.builder().action(WaitStepAction.MARK_AS_FAIL).build());
    response = waitStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertEquals(response.getStatus(), Status.FAILED);
    responseDataMap.put(correlationId, WaitStepResponseData.builder().action(WaitStepAction.MARK_AS_SUCCESS).build());
    response = waitStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertEquals(response.getStatus(), Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testExecuteAsync() {
    doReturn(null).when(waitStepService).save(any());
    AsyncExecutableResponse response = waitStep.executeAsync(ambiance, stepElementParameters, null, null);
    assertEquals(response.getTimeout(), duration.getTimeoutInMillis());
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertEquals(waitStep.getStepParametersClass(), StepElementParameters.class);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testHandleAbort() {
    waitStep.handleAbort(null, null, null);
  }
}
