/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
@Builder
public class TimeSeriesSampleDTO implements Comparable<TimeSeriesSampleDTO> {
  private String txnName;
  private String metricName;
  private Double metricValue;
  private Long timestamp;

  @Override
  public int compareTo(@NotNull TimeSeriesSampleDTO o) {
    if (!this.txnName.equals(o.getTxnName())) {
      return this.txnName.compareTo(o.getTxnName());
    }
    if (!this.metricName.equals(o.getMetricName())) {
      return this.metricName.compareTo(o.getMetricName());
    }
    return this.timestamp.compareTo(o.timestamp);
  }
}
