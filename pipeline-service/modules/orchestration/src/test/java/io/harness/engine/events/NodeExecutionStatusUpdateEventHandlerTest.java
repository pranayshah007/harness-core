/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.events;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;
import io.harness.timeout.TimeoutEngine;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionStatusUpdateEventHandlerTest extends OrchestrationTestBase {
  @Mock ExecutorService executorService;
  @Mock private TimeoutEngine timeoutEngine;

  @Inject @InjectMocks NodeExecutionStatusUpdateEventHandler handler;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnStatusNodeUpdateWhenTimeoutInstanceIdsIsEmpty() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().build()).build();

    handler.onNodeStatusUpdate(nodeUpdateInfo);

    verify(timeoutEngine, never()).onEvent(anyList(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnStatusNodeUpdate() {
    List<String> timeoutInstanceIds = Lists.newArrayList(generateUuid());
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder().status(Status.SUCCEEDED).timeoutInstanceIds(timeoutInstanceIds).build())
            .build();

    ArgumentCaptor<List> timeoutInstanceIdsCaptor = ArgumentCaptor.forClass(List.class);

    doNothing().when(timeoutEngine).onEvent(anyList(), any());
    handler.onNodeStatusUpdate(nodeUpdateInfo);

    verify(timeoutEngine, only()).onEvent(timeoutInstanceIdsCaptor.capture(), any());
    List capture = timeoutInstanceIdsCaptor.getValue();

    assertThat(capture).isEqualTo(timeoutInstanceIds);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetInformExecutorService() {
    ExecutorService informExecutorService = handler.getInformExecutorService();
    assertThat(informExecutorService).isEqualTo(executorService);
  }
}
