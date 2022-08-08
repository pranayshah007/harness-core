/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
public class TimeSeriesDataCollectionRecord {
  private String accountId;
  private String verificationTaskId;
  private String host;
  private long timeStamp;
  private Set<TimeSeriesDataRecordMetricValue> metricValues;

  @Data
  @Builder
  @EqualsAndHashCode(of = "metricName")
  public static class TimeSeriesDataRecordMetricValue {
    private String metricName;
    private String metricIdentifier;
    private Set<TimeSeriesDataRecordGroupValue> timeSeriesValues;
  }

  @Data
  @Builder
  @EqualsAndHashCode(of = "groupName")
  public static class TimeSeriesDataRecordGroupValue {
    private String groupName;
    private double value;
    private Double percent;
  }
}
