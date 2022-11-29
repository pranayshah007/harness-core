/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import lombok.experimental.UtilityClass;
import org.joda.time.DateTime;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class DateTimeUtils {
  public static final String UTC_TIMEZONE = "UTC";

  // DO NOT CHANGE THESE
  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  public String formatDate(TemporalAccessor date) {
    return DATE_FORMATTER.format(date);
  }

  public String formatDateTime(TemporalAccessor date) {
    return DATETIME_FORMATTER.format(date);
  }

  public OffsetDateTime fromJodaDateTime2OffestDateTime(DateTime dateTimeForConversion) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(dateTimeForConversion.getMillis()), ZoneId.of(dateTimeForConversion.getZone().getID()));
  }
}
