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
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator;

public class RatioServiceLevelIndicatorTransformer
    extends ServiceLevelIndicatorTransformer<RatioServiceLevelIndicator, WindowBasedServiceLevelIndicatorSpec> {
  @Override
  public RatioServiceLevelIndicator getEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      boolean isEnabled) {
    RatioSLIMetricSpec ratioSLIMetricSpec =
        (RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec()).getSpec();
    return RatioServiceLevelIndicator.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelIndicatorDTO.getIdentifier())
        .sliMissingDataType(serviceLevelIndicatorDTO.getSliMissingDataType())
        .name(serviceLevelIndicatorDTO.getName())
        .metric1(ratioSLIMetricSpec.getMetric1())
        .metric2(ratioSLIMetricSpec.getMetric2())
        .eventType(ratioSLIMetricSpec.getEventType())
        .thresholdValue(ratioSLIMetricSpec.getThresholdValue())
        .thresholdType(ratioSLIMetricSpec.getThresholdType())
        .monitoredServiceIdentifier(monitoredServiceIndicator)
        .healthSourceIdentifier(healthSourceIndicator)
        .enabled(isEnabled)
        .build();
  }

  @Override
  protected WindowBasedServiceLevelIndicatorSpec getSpec(RatioServiceLevelIndicator serviceLevelIndicator) {
    return WindowBasedServiceLevelIndicatorSpec.builder()
        .type(SLIMetricType.RATIO)
        .spec(RatioSLIMetricSpec.builder()
                  .eventType(serviceLevelIndicator.getEventType())
                  .metric1(serviceLevelIndicator.getMetric1())
                  .metric2(serviceLevelIndicator.getMetric2())
                  .thresholdValue(serviceLevelIndicator.getThresholdValue())
                  .thresholdType(serviceLevelIndicator.getThresholdType())
                  .build())
        .build();
  }
}
