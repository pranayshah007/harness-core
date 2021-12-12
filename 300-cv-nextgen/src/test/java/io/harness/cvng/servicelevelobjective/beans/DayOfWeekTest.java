package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DayOfWeekTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetJavaDayOfWeek() {
    assertThat(DayOfWeek.MONDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    assertThat(DayOfWeek.THURSDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
    assertThat(DayOfWeek.WEDNESDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.WEDNESDAY);
    assertThat(DayOfWeek.THURSDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
    assertThat(DayOfWeek.FRIDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
    assertThat(DayOfWeek.SATURDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
    assertThat(DayOfWeek.SUNDAY.getJavaDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
  }
}