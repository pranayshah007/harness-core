/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DurationDTOTest {
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testRoundDownTo5MinBoundary() {
    assertThat(DurationDTO.findClosestGreaterDurationDTO(Duration.ofHours(20)))
        .isEqualTo(DurationDTO.TWENTY_FOUR_HOURS);
    assertThat(DurationDTO.findClosestGreaterDurationDTO(Duration.ofHours(1))).isEqualTo(DurationDTO.FOUR_HOURS);
    assertThat(DurationDTO.findClosestGreaterDurationDTO(Duration.ofHours(24)))
        .isEqualTo(DurationDTO.TWENTY_FOUR_HOURS);
    assertThat(DurationDTO.findClosestGreaterDurationDTO(Duration.ofDays(5))).isEqualTo(DurationDTO.SEVEN_DAYS);
    assertThat(DurationDTO.findClosestGreaterDurationDTO(Duration.ofDays(10))).isEqualTo(DurationDTO.THIRTY_DAYS);
  }
}
