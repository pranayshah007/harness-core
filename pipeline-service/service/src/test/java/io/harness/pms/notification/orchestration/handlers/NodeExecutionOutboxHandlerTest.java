/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandlerTest extends CategoryTest {
  @Spy @InjectMocks NodeExecutionOutboxHandler nodeExecutionOutboxHandler;

  private NodeStartInfo nodeStartInfo;
  private NodeUpdateInfo nodeUpdateInfo;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    nodeStartInfo =
        NodeStartInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("PIPELINE")
                    .build())
            .build();
    nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("PIPELINE")
                    .build())
            .build();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testOnNodeStartAndUpdateEvents() {
    nodeExecutionOutboxHandler.onNodeStart(nodeStartInfo);
    verify(nodeExecutionOutboxHandler, times(1)).sendOutboxEvents(any());
    nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionOutboxHandler, times(2)).sendOutboxEvents(any());
  }
}
