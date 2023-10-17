/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum QuarterStart {
  @JsonProperty("Jan-Apr-Jul-Oct") JAN_APR_JUL_OCT(1),
  @JsonProperty("Feb-May-Aug-Nov") FEB_MAY_AUG_NOV(2),
  @JsonProperty("Mar-Jun-Sep-Dec") MAR_JUN_SEP_DEC(3);

  Integer startQuarter;

  QuarterStart(Integer startQuarter) {
    this.startQuarter = startQuarter;
  }

  public Integer getStartQuarter() {
    return this.startQuarter;
  }

  public static LocalDate getFirstDayOfQuarter(QuarterStart quarterStart, LocalDateTime currentDateTime) {
    Map<String, List<Integer>> quarterToMonthMap = new HashMap<>();
    quarterToMonthMap.put("1", Arrays.asList(1, 4, 7, 10));
    quarterToMonthMap.put("2", Arrays.asList(2, 5, 8, 11));
    quarterToMonthMap.put("3", Arrays.asList(3, 6, 9, 12));
    Integer startQuarter;
    if (quarterStart == null) {
      startQuarter = QuarterStart.JAN_APR_JUL_OCT.getStartQuarter();
    } else {
      startQuarter = quarterStart.getStartQuarter();
    }
    switch (startQuarter) {
      case 2:
        return getCorrectMonth(currentDateTime, quarterToMonthMap.get("2"));
      case 3:
        return getCorrectMonth(currentDateTime, quarterToMonthMap.get("3"));
      default:
        return getCorrectMonth(currentDateTime, quarterToMonthMap.get("1"));
    }
  }
  public static LocalDate getCorrectMonth(LocalDateTime currentDateTime, List<Integer> list) {
    int startMonth = Integer.MIN_VALUE;
    int month = currentDateTime.getMonthValue();
    for (Integer ele : list) {
      if (ele <= month) {
        startMonth = ele;
      }
    }
    if (startMonth == Integer.MIN_VALUE) {
      startMonth = list.get(list.size() - 1);
    }
    return LocalDate.of(currentDateTime.getYear(), Month.of(startMonth), currentDateTime.getDayOfMonth())
        .with(TemporalAdjusters.firstDayOfMonth());
  }
}
