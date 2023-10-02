/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DateUtils {
  public long parseTimestamp(String timestamp, String format) {
    try {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date date = dateFormat.parse(timestamp);
      return date.getTime();
    } catch (ParseException e) {
      throw new InvalidRequestException(format("Cannot parse date time value %s", timestamp));
    }
  }
}
