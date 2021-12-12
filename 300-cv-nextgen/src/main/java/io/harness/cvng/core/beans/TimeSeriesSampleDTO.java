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
