/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.dashboards;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.TimeUnit;

@OwnedBy(PIPELINE)
public enum GroupBy {
  DAY("day", TimeUnit.DAYS.toMillis(1)),
  WEEK("week", TimeUnit.DAYS.toMillis(7)),
  MONTH("month", TimeUnit.DAYS.toMillis(30));

  private final String datePart;
  private final long noOfMilliseconds;

  GroupBy(String datePart, long noOfMilliseconds) {
    this.datePart = datePart;
    this.noOfMilliseconds = noOfMilliseconds;
  }

  public String getDatePart() {
    return datePart;
  }

  public long getNoOfMilliseconds() {
    return noOfMilliseconds;
  }
}
