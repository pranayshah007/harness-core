/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.verificationjob.beans.AdditionalInfo;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Data
public abstract class CanaryBlueGreenAdditionalInfo extends AdditionalInfo {
  Set<HostSummaryInfo> primary;
  Set<HostSummaryInfo> canary;

  private String primaryInstancesLabel;
  private String canaryInstancesLabel;

  TrafficSplitPercentage trafficSplitPercentage;

  @Data
  @Builder
  @EqualsAndHashCode(of = "hostName")
  public static class HostSummaryInfo {
    String hostName;
    Risk risk;
    long anomalousMetricsCount;
    long anomalousLogClustersCount;
  }

  @Value
  @Builder
  public static class TrafficSplitPercentage {
    double preDeploymentPercentage;
    double postDeploymentPercentage;
  }

  public abstract void setFieldNames();
}
