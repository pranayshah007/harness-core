/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RefObjectUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetOutcomeRefObject() {
    String name = "name";
    assertThat(RefObjectUtils.getOutcomeRefObject(name))
        .isEqualTo(RefObject.newBuilder()
                       .setName(name)
                       .setKey(name)
                       .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                       .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetOutcomeRefObjectUsingProducerId() {
    String name = "name";
    assertThat(RefObjectUtils.getOutcomeRefObject(name, "producerId", null))
        .isEqualTo(RefObject.newBuilder()
                       .setName(name)
                       .setProducerId("producerId")
                       .setKey(name)
                       .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                       .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSweepingOutputRefObject() {
    String name = "name";
    assertThat(RefObjectUtils.getSweepingOutputRefObject(name, null, "key"))
        .isEqualTo(RefObject.newBuilder()
                       .setName(name)
                       .setProducerId("__PRODUCER_ID__")
                       .setKey("key")
                       .setRefType(RefType.newBuilder().setType(OrchestrationRefType.SWEEPING_OUTPUT).build())
                       .build());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetSweepingOutputRefObjectWithGroupName() {
    String name = "name";
    String groupName = "groupName";
    assertThat(RefObjectUtils.getSweepingOutputRefObjectUsingGroup(name, groupName))
        .isEqualTo(RefObject.newBuilder()
                       .setName(name)
                       .setKey(name)
                       .setRefType(RefType.newBuilder().setType(OrchestrationRefType.SWEEPING_OUTPUT).build())
                       .setGroupName(groupName)
                       .build());
  }
}
