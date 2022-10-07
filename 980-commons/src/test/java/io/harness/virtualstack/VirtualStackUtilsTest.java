/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.virtualstack;

import static io.harness.manage.GlobalContextManager.initGlobalContextGuard;
import static io.harness.manage.GlobalContextManager.upsertGlobalContextRecord;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CommonsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.MdcGlobalContextData;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializerWrapper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class VirtualStackUtilsTest extends CommonsTestBase {
  @Inject KryoSerializerWrapper kryoSerializerWrapper;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testPopulateRequest() {
    VirtualStackRequest virtualStackRequest = VirtualStackUtils.populateRequest(kryoSerializerWrapper);
    // expect empty map
    assertThat(virtualStackRequest.getGlobalContext().size()).isEqualTo(9);

    initGlobalContextGuard(null);
    upsertGlobalContextRecord(MdcGlobalContextData.builder().map(ImmutableMap.of("foo", "bar")).build());

    virtualStackRequest = VirtualStackUtils.populateRequest(kryoSerializerWrapper);
    // expect map with some data
    assertThat(virtualStackRequest.getGlobalContext().size()).isGreaterThan(9);

    upsertGlobalContextRecord(() -> "foo");

    virtualStackRequest = VirtualStackUtils.populateRequest(kryoSerializerWrapper);
    // expect no object, because kryo throws exception
    assertThat(virtualStackRequest.getGlobalContext().size()).isEqualTo(0);
  }
}
