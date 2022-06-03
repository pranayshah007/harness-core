/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.ignore;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.IgnoreFailureAdvise;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class IgnoreAdviserTest extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks IgnoreAdviser ignoreAdviser;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testOnAdviseEvent() {
    assertThat(ignoreAdviser.onAdviseEvent(AdvisingEvent.builder().build()))
        .isEqualTo(AdviserResponse.newBuilder()
                       .setIgnoreFailureAdvise(IgnoreFailureAdvise.newBuilder().build())
                       .setType(AdviseType.IGNORE_FAILURE)
                       .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCanAdvise() {
    AdvisingEvent advisingEvent =
        AdvisingEvent.builder()
            .toStatus(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.APPLICATION_FAILURE).build())
            .build();
    when(kryoSerializer.asObject(nullable(byte[].class))).thenReturn(IgnoreAdviserParameters.builder().build());
    assertThat(ignoreAdviser.canAdvise(advisingEvent)).isFalse();
  }
}
