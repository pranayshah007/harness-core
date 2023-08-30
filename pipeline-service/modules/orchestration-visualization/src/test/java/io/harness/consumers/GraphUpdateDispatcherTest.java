/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.consumers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class GraphUpdateDispatcherTest extends OrchestrationVisualizationTestBase {
  @Mock private Consumer consumer;
  @Mock private GraphGenerationService graphGenerationService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testMessageAck() {
    String planExecutionId = generateUuid();
    String mid3 = generateUuid();
    GraphUpdateDispatcher dispatcher = GraphUpdateDispatcher.builder()
                                           .planExecutionId(planExecutionId)
                                           .messageId(mid3)
                                           .startTs(System.currentTimeMillis())
                                           .consumer(consumer)
                                           .graphGenerationService(graphGenerationService)
                                           .build();
    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);
    dispatcher.run();
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(consumer, times(1)).acknowledge(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues()).containsExactlyInAnyOrder(mid3);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNoMessageAck() {
    String planExecutionId = generateUuid();
    String mid3 = generateUuid();
    GraphUpdateDispatcher dispatcher = GraphUpdateDispatcher.builder()
                                           .planExecutionId(planExecutionId)
                                           .messageId(mid3)
                                           .startTs(System.currentTimeMillis())
                                           .consumer(consumer)
                                           .graphGenerationService(graphGenerationService)
                                           .build();
    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(false);
    dispatcher.run();
  }
}
