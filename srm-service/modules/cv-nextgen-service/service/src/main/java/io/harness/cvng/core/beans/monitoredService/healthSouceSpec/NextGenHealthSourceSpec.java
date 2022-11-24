/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.healthsource.HealthSourceParams;
import io.harness.cvng.core.beans.healthsource.QueryDefinitions;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.MetricPackService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NextGenHealthSourceSpec extends HealthSourceSpec {
  /*
  {
"healthSourceParams": {},
"metricPacks": [
  {}
],
"connectorRef": "string",
"type": "APP_DYNAMICS",
"queryDefinitions": [
  {
    "identifier": "string",
    "name": "string",
    "groupName": "string",
    "metricThresholds": [
      {
        "criteria": "string",
        "groupName": "string",
        "metricIdentifier": "string",
        "metricName": "string",
        "metricType": "string",
        "type": "string",
        "spec": {
          "action": "ignore"
        }
      }
    ],
    "liveMonitoring": true,
    "continuousVerification": true,
    "sli": true,
    "query": "string",
    "riskProfile": {
      "riskCategory": "ERROR",
      "riskThresholdType": [
        "string"
      ]
    },
    "queryParams": {
      "applicationName": "string",
      "serviceInstanceField": "string"
    }
  }
]
}
   */

  DataSourceType type;

  List<QueryDefinitions> queryDefinitions = new ArrayList<>();

  Set<TimeSeriesMetricPackDTO> metricPacks;

  HealthSourceParams healthSourceParams;

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    return null;
  }

  @Override
  public DataSourceType getType() {
    return type;
  }
}