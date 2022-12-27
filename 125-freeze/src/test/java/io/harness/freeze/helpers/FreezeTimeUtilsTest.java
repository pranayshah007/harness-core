/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.Recurrence;
import io.harness.freeze.beans.RecurrenceType;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class FreezeTimeUtilsTest extends CategoryTest {
  public static final String timeZone = "Asia/Calcutta";

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_fetchUpcomingTimeWindows() {
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2022-12-19 05:00 PM");
    freezeWindow.setStartTime("2022-12-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    freezeWindow.setRecurrence(recurrence);
    List<Long> nextIterations = FreezeTimeUtils.fetchUpcomingTimeWindow(Collections.singletonList(freezeWindow));
    assertThat(nextIterations.size()).isEqualTo(10);
    long currTime = new Date().getTime();
    assertThat(currTime).isLessThanOrEqualTo(nextIterations.get(0));
  }
}