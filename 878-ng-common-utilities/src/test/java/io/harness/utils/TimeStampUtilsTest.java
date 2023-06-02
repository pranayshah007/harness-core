/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TimeStampUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetTotalDurationWRTCurrentTimeFromTimeStamp() {
    String timestamp = getTimeStampGreaterCurrentTime();
    String timezone = ZoneId.systemDefault().toString();

    Long totalDuration = TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(timestamp, timezone);

    // Assert that the total duration is a non-negative value
    assertThat(totalDuration).isGreaterThan(0);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetTotalDurationWRTCurrentTimeFromTimeStampInvalidDate() {
    String invalidTimestamp = "2023-05-29 12:00"; // Invalid timestamp format
    String timezone = "UTC";

    // Assert that an IllegalArgumentException is thrown for an invalid date format
    assertThatThrownBy(() -> TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(invalidTimestamp, timezone))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetTotalDurationWRTCurrentTimeFromTimeStampInvalidZone() {
    String invalidTimestamp = getTimeStampGreaterCurrentTime(); // Invalid timestamp format
    String timezone = "Invalid";

    // Assert that an IllegalArgumentException is thrown for an invalid date format
    assertThatThrownBy(() -> TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(invalidTimestamp, timezone));
  }

  private String getTimeStampGreaterCurrentTime() {
    LocalDateTime currentDateTime = LocalDateTime.now();
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    return dtf.format(currentDateTime.plusHours(1));
  }
}
