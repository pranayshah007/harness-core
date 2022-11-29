/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.LocalDate;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DayOfWeekTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetJavaDayOfWeek() {
    assertThat(DayOfWeek.MONDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    assertThat(DayOfWeek.TUESDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.TUESDAY);
    assertThat(DayOfWeek.WEDNESDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.WEDNESDAY);
    assertThat(DayOfWeek.THURSDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
    assertThat(DayOfWeek.FRIDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
    assertThat(DayOfWeek.SATURDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
    assertThat(DayOfWeek.SUNDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextDayOfWeek() {
    assertThat(DayOfWeek.MONDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-06"));
    assertThat(DayOfWeek.TUESDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-07"));
    assertThat(DayOfWeek.WEDNESDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-08"));
    assertThat(DayOfWeek.THURSDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-09"));
    assertThat(DayOfWeek.FRIDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-03"));
    assertThat(DayOfWeek.SATURDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-04"));
    assertThat(DayOfWeek.SUNDAY.getNextDayOfWeek(LocalDate.parse("2021-12-03")))
        .isEqualTo(LocalDate.parse("2021-12-05"));
  }
}
