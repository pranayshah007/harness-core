/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.start;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.DummyExecutionStrategy;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.EngineObtainmentHelper;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeStartEventHandlerTest extends PmsSdkCoreTestBase {
  @Mock ExecutableProcessorFactory executableProcessorFactory;
  @Mock EngineObtainmentHelper engineObtainmentHelper;
  @InjectMocks NodeStartEventHandler nodeStartEventHandler;
  @Mock SdkNodeExecutionService sdkNodeExecutionService;

  private NodeStartEvent nodeStartEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    Mockito.when(executableProcessorFactory.obtainProcessor(ExecutionMode.APPROVAL))
        .thenReturn(new ExecutableProcessor(new DummyExecutionStrategy()));

    ambiance = AmbianceTestUtils.buildAmbiance();
    nodeStartEvent = NodeStartEvent.newBuilder().setAmbiance(ambiance).setMode(ExecutionMode.APPROVAL).build();
  }

  @After
  public void verifyInteractions() {
    Mockito.verifyNoMoreInteractions(executableProcessorFactory);
    Mockito.verifyNoMoreInteractions(engineObtainmentHelper);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(nodeStartEventHandler.getMetricPrefix(nodeStartEvent)).isEqualTo("start_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = nodeStartEventHandler.extraLogProperties(nodeStartEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(1);
    assertThat(metricsMap.get("eventType")).isEqualTo(NodeExecutionEventType.START.name());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(nodeStartEventHandler.extractAmbiance(nodeStartEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContext() {
    nodeStartEventHandler.handleEventWithContext(nodeStartEvent);
    Mockito.verify(executableProcessorFactory).obtainProcessor(ExecutionMode.APPROVAL);
    Mockito.verify(engineObtainmentHelper)
        .obtainInputPackage(nodeStartEvent.getAmbiance(), nodeStartEvent.getRefObjectsList());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContextThrowsException() {
    Exception ex = new InvalidRequestException("Invalid request");
    Mockito
        .when(
            engineObtainmentHelper.obtainInputPackage(nodeStartEvent.getAmbiance(), nodeStartEvent.getRefObjectsList()))
        .thenThrow(ex);
    nodeStartEventHandler.handleEventWithContext(nodeStartEvent);
    Mockito.verify(executableProcessorFactory).obtainProcessor(ExecutionMode.APPROVAL);
    Mockito.verify(engineObtainmentHelper)
        .obtainInputPackage(nodeStartEvent.getAmbiance(), nodeStartEvent.getRefObjectsList());
    Mockito.verify(sdkNodeExecutionService).handleStepResponse(ambiance, NodeExecutionUtils.constructStepResponse(ex));
  }
}
