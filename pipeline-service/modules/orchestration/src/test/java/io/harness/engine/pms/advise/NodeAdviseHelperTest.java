/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeAdviseHelperTest extends OrchestrationTestBase {
  @Mock private NodeAdviseEventPublisher nodeAdviseEventPublisher;

  @Inject @InjectMocks private NodeAdviseHelper helper;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldQueueAdvisingEvent() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    PlanNode planNode = PlanNode.builder().build();
    when(nodeAdviseEventPublisher.publishEvent(nodeExecution, planNode, Status.SUCCEEDED)).thenReturn(null);

    helper.queueAdvisingEvent(nodeExecution, planNode, Status.SUCCEEDED);

    verify(nodeAdviseEventPublisher).publishEvent(nodeExecution, planNode, Status.SUCCEEDED);
  }
}
