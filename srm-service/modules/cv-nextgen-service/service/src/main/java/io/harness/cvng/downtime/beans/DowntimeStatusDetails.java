/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.beans;

import java.time.Clock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DowntimeStatusDetails {
  DowntimeStatus status;
  long startTime;
  long endTime;
  String endDateTime;

  public static DowntimeStatusDetailsBuilder getDowntimeStatusDetailsInstanceBuilder(
      long startTime, long endTime, Clock clock) {
    return DowntimeStatusDetails.builder().startTime(startTime).endTime(endTime).status(
        startTime > clock.millis() / 1000 ? DowntimeStatus.SCHEDULED : DowntimeStatus.ACTIVE);
  }
}
