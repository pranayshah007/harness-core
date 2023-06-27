/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerificationMetricsTimeSeries {
  String verificationId;
  List<HealthSource> healthSources;

  @Data
  @SuperBuilder
  @NoArgsConstructor
  public static class HealthSource {
    String healthSourceIdentifier;
    List<TransactionGroup> transactionGroups;
  }
  @Data
  @SuperBuilder
  @NoArgsConstructor
  public static class TransactionGroup {
    String transactionGroupName;
    List<Metric> metrics;
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  public static class Metric {
    String metricName;
    List<Node> nodes;
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  public static class Node {
    String nodeIdentifier;
    List<TimeSeriesValue> timeSeries;
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  public static class TimeSeriesValue {
    long epochSecond;
    Double metricValue;
  }
}