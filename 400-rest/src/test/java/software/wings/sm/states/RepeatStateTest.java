/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.api.ServiceElement;
import software.wings.beans.ExecutionStrategy;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.RepeatState.RepeatStateExecutionData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * The Class RepeatStateTest.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class RepeatStateTest extends WingsBaseTest {
  @Inject KryoSerializer kryoSerializer;

  /**
   * Should execute serial.
   */

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldExecuteSerial() {
    String stateName = "test";
    WorkflowExecutionService workflowExecutionService = mock(WorkflowExecutionServiceImpl.class);
    List<ContextElement> repeatElements = getTestRepeatElements();

    ExecutionContextImpl context = prepareExecutionContext(stateName, repeatElements);
    when(workflowExecutionService.checkIfOnDemand(any(), any())).thenReturn(false);
    RepeatState repeatState = new RepeatState(stateName);
    repeatState.setWorkflowExecutionService(workflowExecutionService);
    repeatState.setKryoSerializer(kryoSerializer);
    repeatState.setRepeatElementExpression("services()");
    repeatState.setExecutionStrategy(ExecutionStrategy.SERIAL);
    repeatState.setRepeatTransitionStateName("abc");
    repeatState.resolveProperties();

    assertThat(repeatState.getRepeatElementType()).isEqualTo(ContextElementType.SERVICE);

    ExecutionResponse response = repeatState.execute(context, null);

    assertResponse(repeatElements, response, 1);
    RepeatStateExecutionData stateExecutionData = (RepeatStateExecutionData) response.getStateExecutionData();

    assertThat(stateExecutionData.getRepeatElementIndex()).isNotNull();
    assertThat(stateExecutionData.getRepeatElementIndex()).isEqualTo(0);

    assertThat(response.getStateExecutionInstances()).isNotNull();
    assertThat(response.getStateExecutionInstances()).hasSize(1);
  }

  /**
   * Should execute parallel.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldExecuteParallel() {
    List<ContextElement> repeatElements = getTestRepeatElements();
    String stateName = "test";
    WorkflowExecutionService workflowExecutionService = mock(WorkflowExecutionServiceImpl.class);
    when(workflowExecutionService.checkIfOnDemand(any(), any())).thenReturn(false);

    ExecutionContextImpl context = prepareExecutionContext(stateName, repeatElements);

    RepeatState repeatState = new RepeatState(stateName);
    repeatState.setWorkflowExecutionService(workflowExecutionService);
    repeatState.setKryoSerializer(kryoSerializer);
    repeatState.setRepeatElementExpression("services()");
    repeatState.setExecutionStrategy(ExecutionStrategy.PARALLEL);
    repeatState.setRepeatTransitionStateName("abc");
    repeatState.resolveProperties();

    assertThat(repeatState.getRepeatElementType()).isEqualTo(ContextElementType.SERVICE);

    ExecutionResponse response = repeatState.execute(context, null);

    assertResponse(repeatElements, response, 2);
    RepeatStateExecutionData stateExecutionData = (RepeatStateExecutionData) response.getStateExecutionData();

    assertThat(stateExecutionData.getRepeatElementIndex()).isNull();

    assertThat(response.getStateExecutionInstances()).isNotNull();
    assertThat(response.getStateExecutionInstances()).hasSize(2);
  }

  private void assertResponse(List<ContextElement> repeatElements, ExecutionResponse response, int corrIdsExpected) {
    assertThat(response).isNotNull();
    assertThat(response.isAsync()).as("Async Execution").isEqualTo(true);
    assertThat(response.getStateExecutionData()).isNotNull();
    assertThat(response.getStateExecutionData()).isInstanceOf(RepeatStateExecutionData.class);
    RepeatStateExecutionData stateExecutionData = (RepeatStateExecutionData) response.getStateExecutionData();

    assertThat(stateExecutionData.getRepeatElements()).isNotNull();
    assertThat(stateExecutionData.getRepeatElements()).isEqualTo(repeatElements);

    assertThat(response.getCorrelationIds()).isNotNull();
    assertThat(response.getCorrelationIds().size()).as("correlationIds").isEqualTo(corrIdsExpected);

    if (log.isDebugEnabled()) {
      log.debug("correlationIds: " + response.getCorrelationIds());
    }
  }

  private ExecutionContextImpl prepareExecutionContext(String stateName, List<ContextElement> repeatElements) {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setUuid(generateUuid());
    stateExecutionInstance.setDisplayName(stateName);

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.evaluateExpression("services()")).thenReturn(repeatElements);
    when(context.getStateExecutionInstance()).thenReturn(stateExecutionInstance);
    return context;
  }

  private List<ContextElement> getTestRepeatElements() {
    List<ContextElement> repeatElements = new ArrayList<>();
    ServiceElement ui = ServiceElement.builder().name("ui").build();
    repeatElements.add(ui);

    ServiceElement svr = ServiceElement.builder().name("server").build();
    repeatElements.add(svr);
    return repeatElements;
  }
}
