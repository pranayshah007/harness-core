/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionsCacheTest extends CategoryTest {
  NodeExecutionsCache nodeExecutionsCache;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PLAN_EXECUTION_ID").build();
    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  // Have fieldsIncluded to be parentId and fieldExcluded to be Id, so that it uses mongoIndex and no documents are
  // queried in Mongo.
  public void testFindAllChildrenUsingRequiredProjection() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("test").status(Status.ABORTED).build()))
        .when(nodeExecutionService)
        .findAllChildrenWithStatusIn("PLAN_EXECUTION_ID", "PARENT_ID", null, false, true,
            Sets.newHashSet(NodeExecutionKeys.parentId, NodeExecutionKeys.status, NodeExecutionKeys.stepType),
            Collections.emptySet());
    List<Status> allChildren = nodeExecutionsCache.findAllTerminalChildrenStatusOnly("PARENT_ID");
    assertThat(allChildren.size()).isEqualTo(1);
  }
}
