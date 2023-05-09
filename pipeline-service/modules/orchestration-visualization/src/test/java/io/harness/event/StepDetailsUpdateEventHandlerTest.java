/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class StepDetailsUpdateEventHandlerTest extends OrchestrationVisualizationTestBase {
  @InjectMocks StepDetailsUpdateEventHandler stepDetailsUpdateEventHandler;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleEvent() {
    OrchestrationGraph orchestrationGraph =
        OrchestrationGraph.builder()
            .adjacencyList(OrchestrationAdjacencyListInternal.builder().graphVertexMap(Collections.emptyMap()).build())
            .build();

    stepDetailsUpdateEventHandler.handleEvent("planExecutionId", "nodeExecutionId", orchestrationGraph, new Update());
    assertThatCode(()
                       -> stepDetailsUpdateEventHandler.handleEvent(
                           "planExecutionId", "nodeExecutionId", orchestrationGraph, new Update()))
        .doesNotThrowAnyException();
  }
}
