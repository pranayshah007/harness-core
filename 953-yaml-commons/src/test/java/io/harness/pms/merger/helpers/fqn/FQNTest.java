/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers.fqn;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FQNTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetExpressionFqnWithoutIgnoring() {
    FQN fqn = FQN.builder()
                  .fqnList(Arrays.asList(FQNNode.builder().key("step").build(), FQNNode.builder().key("spec").build()))
                  .build();

    // No element in fqnList has nodeType set. So no element will be considered for expression.
    assertThatThrownBy(fqn::getExpressionFqnWithoutIgnoring).isInstanceOf(StringIndexOutOfBoundsException.class);

    // Both elements are of nodeType KEY. Both will be considered for expression.
    fqn.setFqnList(Arrays.asList(FQNNode.builder().key("step").nodeType(FQNNode.NodeType.KEY).build(),
        FQNNode.builder().key("spec").nodeType(FQNNode.NodeType.KEY).build()));
    assertEquals(fqn.getExpressionFqnWithoutIgnoring(), "step.spec");

    // One element is of nodeType KEY_WITH_UUID and one is of KEY. Both will be considered for expression.
    fqn.setFqnList(Arrays.asList(FQNNode.builder().uuidValue("step").nodeType(FQNNode.NodeType.KEY_WITH_UUID).build(),
        FQNNode.builder().key("spec").nodeType(FQNNode.NodeType.KEY).build()));
    assertEquals(fqn.getExpressionFqnWithoutIgnoring(), "step.spec");

    // One element is of nodeType KEY_WITH_UUID and one is of UUID. Both will be considered for expression.
    fqn.setFqnList(Arrays.asList(FQNNode.builder().uuidValue("step").nodeType(FQNNode.NodeType.KEY_WITH_UUID).build(),
        FQNNode.builder().uuidValue("spec").nodeType(FQNNode.NodeType.UUID).build()));
    assertEquals(fqn.getExpressionFqnWithoutIgnoring(), "step.spec");

    // Only one element has the nodeType. So only one element will be considered for expression.
    fqn.setFqnList(Arrays.asList(FQNNode.builder().uuidValue("step").build(),
        FQNNode.builder().uuidValue("spec").nodeType(FQNNode.NodeType.UUID).build()));
    assertEquals(fqn.getExpressionFqnWithoutIgnoring(), "spec");
  }
}
