/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.MetricResponseMappingDTO;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MetricResponseMapping {
  String metricValueJsonPath;
  String timestampJsonPath;
  String serviceInstanceJsonPath;
  String serviceInstanceListJsonPath;
  String relativeMetricListJsonPath;
  String relativeTimestampJsonPath;
  String relativeMetricValueJsonPath;
  String relativeServiceInstanceValueJsonPath;
  String timestampFormat;

  public MetricResponseMappingDTO toDto() {
    return MetricResponseMappingDTO.builder()
        .metricValueJsonPath(metricValueJsonPath)
        .serviceInstanceJsonPath(serviceInstanceJsonPath)
        .timestampJsonPath(timestampJsonPath)
        .timestampFormat(timestampFormat)
        .serviceInstanceListJsonPath(serviceInstanceListJsonPath)
        .relativeMetricListJsonPath(relativeMetricListJsonPath)
        .relativeMetricValueJsonPath(relativeMetricValueJsonPath)
        .relativeTimestampJsonPath(relativeTimestampJsonPath)
        .relativeServiceInstanceValueJsonPath(relativeServiceInstanceValueJsonPath)
        .build();
  }
}
