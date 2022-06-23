/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StoreTypeMapperTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void fromPipelineStoreType() {
    assertThat(StoreTypeMapper.fromPipelineStoreType(null)).isNull();
    assertThat(StoreTypeMapper.fromPipelineStoreType(PipelineStoreType.INLINE)).isEqualTo(StoreType.INLINE);
    assertThat(StoreTypeMapper.fromPipelineStoreType(PipelineStoreType.REMOTE)).isEqualTo(StoreType.REMOTE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void fromStoreType() {
    assertThat(StoreTypeMapper.fromStoreType(null)).isNull();
    assertThat(StoreTypeMapper.fromStoreType(StoreType.INLINE)).isEqualTo(PipelineStoreType.INLINE);
    assertThat(StoreTypeMapper.fromStoreType(StoreType.REMOTE)).isEqualTo(PipelineStoreType.REMOTE);
  }
}
