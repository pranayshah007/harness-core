/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.core.beans.monitoredService.RiskData;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CV)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceDependencyGraphDTO {
  List<ServiceSummaryDetails> nodes;
  List<Edge> edges;

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class ServiceSummaryDetails {
    String identifierRef;
    String serviceRef;
    String serviceName;
    String environmentRef;
    String environmentName;
    @Deprecated double riskScore;
    @Deprecated Risk riskLevel;
    RiskData riskData;
    MonitoredServiceType type;
  }

  @Data
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Edge {
    String from;
    String to;
  }
}
