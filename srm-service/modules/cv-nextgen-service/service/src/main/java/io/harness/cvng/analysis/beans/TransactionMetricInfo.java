/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.beans.DataSourceType;

import java.util.SortedSet;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class TransactionMetricInfo implements Comparable<TransactionMetricInfo> {
  private TransactionMetric transactionMetric;
  private String connectorName;
  private DataSourceType dataSourceType;
  private SortedSet<DeploymentTimeSeriesAnalysisDTO.HostData> nodes;
  private NodeRiskCountDTO nodeRiskCountDTO;

  @Value
  @Builder
  @EqualsAndHashCode(of = {"transactionName", "metricName"})
  public static class TransactionMetric {
    String transactionName;
    String metricName;
    private Double score; // is this score is needed in the UI?
    private Risk risk;
  }

  @Override
  public int compareTo(@NotNull TransactionMetricInfo o) {
    int result = Integer.compare(
        o.getTransactionMetric().getRisk().getValue(), this.getTransactionMetric().getRisk().getValue());
    if (result == 0) {
      result =
          this.getTransactionMetric().getTransactionName().compareTo(o.getTransactionMetric().getTransactionName());
    }
    if (result == 0) {
      result = this.getTransactionMetric().getMetricName().compareTo(o.getTransactionMetric().getMetricName());
    }
    return result;
  }
}
