/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class MergePlanCreationResponseTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergePreservedNodesInRollbackMode() {
    MergePlanCreationResponse nullList = MergePlanCreationResponse.builder().build();
    nullList.mergePreservedNodesInRollbackMode(null);
    assertThat(nullList.getPreservedNodesInRollbackMode()).isNull();
    nullList.mergePreservedNodesInRollbackMode(Collections.emptyList());
    assertThat(nullList.getPreservedNodesInRollbackMode()).isNull();

    MergePlanCreationResponse withPreservedNodes =
        MergePlanCreationResponse.builder().preservedNodesInRollbackMode(Collections.singletonList("n1")).build();
    withPreservedNodes.mergePreservedNodesInRollbackMode(null);
    assertThat(withPreservedNodes.getPreservedNodesInRollbackMode()).containsExactly("n1");
    withPreservedNodes.mergePreservedNodesInRollbackMode(Collections.emptyList());
    assertThat(withPreservedNodes.getPreservedNodesInRollbackMode()).containsExactly("n1");
    withPreservedNodes.mergePreservedNodesInRollbackMode(Collections.singletonList("n2"));
    assertThat(withPreservedNodes.getPreservedNodesInRollbackMode()).containsExactly("n1", "n2");
    withPreservedNodes.mergePreservedNodesInRollbackMode(Arrays.asList("n3", "n4"));
    assertThat(withPreservedNodes.getPreservedNodesInRollbackMode()).containsExactly("n1", "n2", "n3", "n4");
  }
}