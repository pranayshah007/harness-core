/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AdviserTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetAllFailureTypes() {
    Adviser adviser = new TestAdvisor();
    AdvisingEvent advisingEvent = AdvisingEvent.builder().build();
    List<FailureType> failureTypes = adviser.getAllFailureTypes(advisingEvent);
    // FailureInfo is null. SO UNKNOWN_FAILURE will be returned.
    assertThat(failureTypes).isEqualTo(Collections.singletonList(FailureType.UNKNOWN_FAILURE));

    // FailureTypes in failureInfo.failureData, So it will be present in the returned failureTypes list.
    advisingEvent = AdvisingEvent.builder()
                        .failureInfo(FailureInfo.newBuilder()
                                         .addFailureData(FailureData.newBuilder()
                                                             .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                             .addFailureTypes(FailureType.AUTHORIZATION_FAILURE)
                                                             .build())
                                         .build())
                        .build();
    failureTypes = adviser.getAllFailureTypes(advisingEvent);
    assertThat(failureTypes.size()).isEqualTo(2);
    assertThat(failureTypes.contains(FailureType.APPLICATION_FAILURE)).isTrue();
    assertThat(failureTypes.contains(FailureType.AUTHORIZATION_FAILURE)).isTrue();

    // FailureTypes in failureInfo, So it will be present in the returned failureTypes list.
    advisingEvent =
        AdvisingEvent.builder()
            .failureInfo(FailureInfo.newBuilder().addFailureTypes(FailureType.AUTHENTICATION_FAILURE).build())
            .build();
    failureTypes = adviser.getAllFailureTypes(advisingEvent);
    assertThat(failureTypes.size()).isEqualTo(1);
    assertThat(failureTypes.contains(FailureType.AUTHENTICATION_FAILURE)).isTrue();

    // FailureTypes in failureInfo and inside failureInfo.failureData, So both will be present in the returned
    // failureTypes list.
    advisingEvent = AdvisingEvent.builder()
                        .failureInfo(FailureInfo.newBuilder()
                                         .addFailureTypes(FailureType.AUTHENTICATION_FAILURE)
                                         .addFailureData(FailureData.newBuilder()
                                                             .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                             .addFailureTypes(FailureType.AUTHORIZATION_FAILURE)
                                                             .build())
                                         .build())
                        .build();
    failureTypes = adviser.getAllFailureTypes(advisingEvent);
    assertThat(failureTypes.size()).isEqualTo(3);
    assertThat(failureTypes.contains(FailureType.AUTHENTICATION_FAILURE)).isTrue();
    assertThat(failureTypes.contains(FailureType.AUTHORIZATION_FAILURE)).isTrue();
    assertThat(failureTypes.contains(FailureType.APPLICATION_FAILURE)).isTrue();

    // No failureTypes in the failureInfo. So UNKNOWN_FAILURE should be returned.
    advisingEvent = AdvisingEvent.builder().failureInfo(FailureInfo.newBuilder().build()).build();
    failureTypes = adviser.getAllFailureTypes(advisingEvent);
    assertThat(failureTypes).isEqualTo(Collections.singletonList(FailureType.UNKNOWN_FAILURE));
  }

  private static class TestAdvisor implements Adviser {
    @Override
    public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
      return null;
    }

    @Override
    public boolean canAdvise(AdvisingEvent advisingEvent) {
      return false;
    }
  }
}
