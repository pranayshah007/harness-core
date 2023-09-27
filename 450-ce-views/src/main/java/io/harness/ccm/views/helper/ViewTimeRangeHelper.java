/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ccm.views.dto.ViewTimeRangeDto;
import io.harness.ccm.views.dto.ViewTimeRangeDto.ViewTimeRangeDtoBuilder;
import io.harness.ccm.views.entities.ViewTimeRange;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.govern.Switch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

@CodePulse(
    module = ProductModule.CCM, unitCoverageRequired = true, components = {HarnessModuleComponent.CCM_PERSPECTIVE})
public class ViewTimeRangeHelper {
  private static final String DEFAULT_TIMEZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000L;

  public ViewTimeRangeDto getStartEndTime(ViewTimeRange viewTimeRange) {
    ViewTimeRangeType viewTimeRangeType = viewTimeRange.getViewTimeRangeType();
    ViewTimeRangeDtoBuilder timeRangeDtoBuilder = ViewTimeRangeDto.builder();
    long startOfDayTimestamp = getStartOfDayTimestamp(0);
    switch (viewTimeRangeType) {
      case LAST_7:
        timeRangeDtoBuilder.startTime(subMillis(startOfDayTimestamp, 7 * ONE_DAY_MILLIS))
            .endTime(subMillis(startOfDayTimestamp, 1))
            .build();
        break;
      case LAST_30:
        timeRangeDtoBuilder.startTime(subMillis(startOfDayTimestamp, 30 * ONE_DAY_MILLIS))
            .endTime(subMillis(startOfDayTimestamp, 1))
            .build();
        break;
      case CURRENT_MONTH:
        timeRangeDtoBuilder.startTime(getStartOfMonth(0)).endTime(subMillis(startOfDayTimestamp, 1)).build();
        break;
      case LAST_MONTH:
        timeRangeDtoBuilder.startTime(getStartOfMonth(-1)).endTime(subMillis(getStartOfMonth(0), 1)).build();
        break;
      case CUSTOM:
        timeRangeDtoBuilder.startTime(viewTimeRange.getStartTime()).endTime(viewTimeRange.getEndTime()).build();
        break;
      default:
        Switch.unhandled(viewTimeRangeType);
    }
    return timeRangeDtoBuilder.build();
  }

  private long subMillis(long timestamp, long period) {
    return Instant.ofEpochMilli(timestamp - period).toEpochMilli();
  }
  private long getStartOfDayTimestamp(long offset) {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return (zdtStart.toEpochSecond() * 1000) - offset;
  }

  private long getStartOfMonth(int subMonth) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    c.add(Calendar.MONTH, subMonth);
    return c.getTimeInMillis();
  }
}
