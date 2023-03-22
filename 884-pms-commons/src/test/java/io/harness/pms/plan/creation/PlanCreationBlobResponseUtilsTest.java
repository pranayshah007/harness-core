/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PlanCreationBlobResponseUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergePreservedNodesInRollbackMode() {
    PlanCreationBlobResponse.Builder emptyBuilder = PlanCreationBlobResponse.newBuilder();
    PlanCreationBlobResponse noPreservedNodes = PlanCreationBlobResponse.newBuilder().build();

    PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(emptyBuilder, noPreservedNodes);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).hasSize(0);

    PlanCreationBlobResponse emptyPreservedNodesList =
        PlanCreationBlobResponse.newBuilder().addAllPreservedNodesInRollbackMode(Collections.emptyList()).build();
    PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(emptyBuilder, emptyPreservedNodesList);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).hasSize(0);

    PlanCreationBlobResponse onePreservedNode = PlanCreationBlobResponse.newBuilder()
                                                    .addAllPreservedNodesInRollbackMode(Collections.singletonList("n1"))
                                                    .build();
    PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(emptyBuilder, onePreservedNode);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).hasSize(1);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).contains("n1");

    PlanCreationBlobResponse oneMorePreservedNode =
        PlanCreationBlobResponse.newBuilder()
            .addAllPreservedNodesInRollbackMode(Collections.singletonList("n2"))
            .build();
    PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(emptyBuilder, oneMorePreservedNode);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).hasSize(2);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).contains("n1", "n2");

    PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(emptyBuilder, noPreservedNodes);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).hasSize(2);
    assertThat(emptyBuilder.getPreservedNodesInRollbackModeList()).contains("n1", "n2");
  }
}