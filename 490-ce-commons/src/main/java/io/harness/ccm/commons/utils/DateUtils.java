/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CE)
public class DateUtils {
  private static final String MONTH_TWO_DIGITS_FORMAT_SPECIFIER = "%02d";
  private static final String YYYY_MM = "%s%s";

  private List<LocalDateTime> getLastTwelveMonthsFirstDay() {
    final List<LocalDateTime> localDateTimes = new ArrayList<>();

    final LocalDate currentDate = LocalDate.now();
    final int currentYear = currentDate.getYear();
    final Month currentMonth = currentDate.getMonth();

    // Generate timestamps for the first day of each month for the last 12 months
    for (int monthsToSubtract = 0; monthsToSubtract < 12; monthsToSubtract++) {
      final Month month = currentMonth.minus(monthsToSubtract);
      int year = currentYear;
      if (month.compareTo(currentMonth) > 0) {
        year--;
      }
      final LocalDateTime timestamp = LocalDateTime.of(year, month, 1, 0, 0, 0);
      localDateTimes.add(timestamp);
    }
    return localDateTimes;
  }

  public List<String> getLastTwelveMonthsGCPInvoiceMonth() {
    return getLastTwelveMonthsFirstDay()
        .stream()
        .map(localDateTime
            -> String.format(YYYY_MM, localDateTime.getYear(),
                String.format(MONTH_TWO_DIGITS_FORMAT_SPECIFIER, localDateTime.getMonth().getValue())))
        .collect(Collectors.toList());
  }
}
