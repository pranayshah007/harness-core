/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.change.ChangeSummaryDTO;
import io.harness.gitsync.beans.YamlDTO;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MonitoredServiceListItemDTO implements YamlDTO {
  String name;
  String identifier;
  String serviceRef;
  String environmentRef;
  List<String> environmentRefList;
  String serviceName;
  String environmentName;
  MonitoredServiceType type;
  boolean healthMonitoringEnabled;
  RiskData currentHealthScore;
  List<RiskData> dependentHealthScore;
  HistoricalTrend historicalTrend;
  ChangeSummaryDTO changeSummary;
  Map<String, String> tags;
  boolean serviceMonitoringEnabled;

  List<SloHealthIndicatorDTO> sloHealthIndicators;

  public static class MonitoredServiceListItemDTOBuilder {
    public String getServiceRef() {
      return serviceRef;
    }

    public String getEnvironmentRef() {
      return environmentRef;
    }

    public String getIdentifier() {
      return identifier;
    }

    public HistoricalTrend getHistoricalTrend() {
      return historicalTrend;
    }
  }
}
