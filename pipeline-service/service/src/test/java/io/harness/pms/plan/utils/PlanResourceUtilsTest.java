/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.utils;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanResourceUtilsTest extends CategoryTest {
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testThrowErrorIfAllStagesAreDeleted() {
    long currentTime = System.currentTimeMillis();

    // for days gap less than 30 days
    long createdAt = currentTime - 29 * DAY_IN_MS;
    assertThat(PlanResourceUtility.validateInTimeLimitForRetry(createdAt)).isEqualTo(true);

    // for days gap equals to 30 days
    createdAt = currentTime - 30 * DAY_IN_MS;
    assertThat(PlanResourceUtility.validateInTimeLimitForRetry(createdAt)).isEqualTo(false);

    // for days gap greator than 30 days
    createdAt = currentTime - 31 * DAY_IN_MS;
    assertThat(PlanResourceUtility.validateInTimeLimitForRetry(createdAt)).isEqualTo(false);
  }
}
