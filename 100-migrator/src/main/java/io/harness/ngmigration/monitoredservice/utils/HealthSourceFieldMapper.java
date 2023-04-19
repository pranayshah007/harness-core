/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.utils;

import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskCategory;
import io.harness.cvng.core.beans.RiskProfile;

import software.wings.metrics.MetricType;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class HealthSourceFieldMapper {
  public HealthSourceMetricDefinition.AnalysisDTO getAnalysisDTO(String metricType, String serviceInstanceIdentifier) {
    return HealthSourceMetricDefinition.AnalysisDTO.builder()
        .riskProfile(RiskProfile.builder()
                         .riskCategory(riskCategoryFromMetricType(metricType))
                         .thresholdTypes(Arrays.asList(
                             TimeSeriesThresholdType.ACT_WHEN_HIGHER, TimeSeriesThresholdType.ACT_WHEN_LOWER))
                         .build())
        .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                    .enabled(true)
                                    .serviceInstanceFieldName(serviceInstanceIdentifier)
                                    .build())
        .build();
  }

  public RiskCategory riskCategoryFromMetricType(String metricType) {
    if (StringUtils.isEmpty(metricType)
        || Arrays.asList(MetricType.values())
               .stream()
               .map(enumValue -> enumValue.name())
               .noneMatch(stringName -> stringName.equals(metricType))) {
      return RiskCategory.PERFORMANCE_OTHER;
    }
    MetricType metricTypeEnum = MetricType.valueOf(metricType);
    switch (metricTypeEnum) {
      case ERROR:
        return RiskCategory.ERROR;
      case INFRA:
        return RiskCategory.INFRASTRUCTURE;
      case RESP_TIME:
        return RiskCategory.PERFORMANCE_RESPONSE_TIME;
      case THROUGHPUT:
        return RiskCategory.PERFORMANCE_THROUGHPUT;
      default:
        return RiskCategory.PERFORMANCE_OTHER;
    }
  }
}
