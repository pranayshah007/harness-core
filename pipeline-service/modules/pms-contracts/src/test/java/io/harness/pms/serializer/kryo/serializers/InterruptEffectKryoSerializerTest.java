/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptEffectKryoSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInstance() {
    InterruptEffectKryoSerializer interruptEffectKryoSerializer = InterruptEffectKryoSerializer.getInstance();
    assertThat(interruptEffectKryoSerializer).isNotEqualTo(null);
  }
}
