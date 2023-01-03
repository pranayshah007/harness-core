/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesMetricPackDTO {
  @NotNull private String identifier;
  List<MetricThreshold> metricThresholds;

  public MetricPack toMetricPack(String accountId, String orgId, String projectId, DataSourceType dataSourceType,
      MetricPackService metricPackService) {
    return metricPackService.getMetricPack(accountId, orgId, projectId, dataSourceType, identifier);
  }

  public static TimeSeriesMetricPackDTO toMetricPackDTO(MetricPack metricPack) {
    return TimeSeriesMetricPackDTO.builder().identifier(metricPack.getIdentifier()).build();
  }
}
