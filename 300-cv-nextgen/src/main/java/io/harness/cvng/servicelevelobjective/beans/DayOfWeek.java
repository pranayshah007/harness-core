package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum DayOfWeek {
  @JsonProperty("Mon") MONDAY(1),
  @JsonProperty("Tue") TUESDAY(2),
  @JsonProperty("Wed") WEDNESDAY(3),
  @JsonProperty("Thu") THURSDAY(4),
  @JsonProperty("Fri") FRIDAY(5),
  @JsonProperty("Sat") SATURDAY(6),
  @JsonProperty("Sun") SUNDAY(7);
  int dayOfWeek;
  DayOfWeek(int dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public java.time.DayOfWeek getJavaDayOfWeek() {
    return java.time.DayOfWeek.of(this.dayOfWeek);
  }
  public LocalDate getNextDayOfWeek(LocalDate currentDate) {
    return currentDate.with(TemporalAdjusters.next(this.getJavaDayOfWeek()));
  }
}
