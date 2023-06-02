/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dto.LevelDTO;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PlanExecutionUtilsTest extends CategoryTest {
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testExtractPlan() {
    PlanCreationBlobResponse planCreationBlobResponse =
        PlanCreationBlobResponse.newBuilder()
            .putNodes("plan_node_proto_element", PlanNodeProto.newBuilder().build())
            .setStartingNodeId("startingNodeId")
            .setGraphLayoutInfo(GraphLayoutInfo.newBuilder().build())
            .build();
    Plan plan = PlanExecutionUtils.extractPlan("planNodeUuid", planCreationBlobResponse);
    assertThat(plan.getStartingNodeId()).isEqualTo("startingNodeId");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetFQNUsingLevelDTOs() {
    LevelDTO level = LevelDTO.builder().identifier("identifier").setupId("setupId").skipExpressionChain(false).build();
    String fqn = PlanExecutionUtils.getFQNUsingLevelDTOs(Collections.singletonList(level));
    assertThat(fqn).isEqualTo("identifier");
  }
}
