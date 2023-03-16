/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;

public class ThresholdServiceLevelIndicatorTransformer
    extends ServiceLevelIndicatorTransformer<ThresholdServiceLevelIndicator, WindowBasedServiceLevelIndicatorSpec> {
  @Override
  public ThresholdServiceLevelIndicator getEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIdentifier,
      String healthSourceIdentifier, boolean isEnabled) {
    ThresholdSLIMetricSpec thresholdSLIMetricSpec =
        (ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec()).getSpec();

    return ThresholdServiceLevelIndicator.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelIndicatorDTO.getIdentifier())
        .name(serviceLevelIndicatorDTO.getName())
        .sliMissingDataType(serviceLevelIndicatorDTO.getSliMissingDataType())
        .metric1(thresholdSLIMetricSpec.getMetric1())
        .thresholdValue(thresholdSLIMetricSpec.getThresholdValue())
        .thresholdType(thresholdSLIMetricSpec.getThresholdType())
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .healthSourceIdentifier(healthSourceIdentifier)
        .enabled(isEnabled)
        .build();
  }

  @Override
  protected WindowBasedServiceLevelIndicatorSpec getSpec(ThresholdServiceLevelIndicator serviceLevelIndicator) {
    return WindowBasedServiceLevelIndicatorSpec.builder()
        .type(SLIMetricType.THRESHOLD)
        .spec(ThresholdSLIMetricSpec.builder()
                  .metric1(serviceLevelIndicator.getMetric1())
                  .thresholdValue(serviceLevelIndicator.getThresholdValue())
                  .thresholdType(serviceLevelIndicator.getThresholdType())
                  .build())
        .build();
  }
}
