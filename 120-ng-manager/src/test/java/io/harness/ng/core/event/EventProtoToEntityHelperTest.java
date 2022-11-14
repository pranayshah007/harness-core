/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EventProtoToEntityHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetEntityTypeProtoEnumToRestEnumMap() {
    final Map<EntityTypeProtoEnum, EntityType> entityTypeProtoEnumToRestEnumMap =
        EventProtoToEntityHelper.getEntityTypeProtoEnumToRestEnumMap();
    assertThat(entityTypeProtoEnumToRestEnumMap.size()).isEqualTo(EntityType.values().length);
  }
}
