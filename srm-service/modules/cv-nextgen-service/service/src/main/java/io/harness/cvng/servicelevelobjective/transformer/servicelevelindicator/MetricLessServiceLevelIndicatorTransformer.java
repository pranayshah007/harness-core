/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slispec.MetricLessServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.MetricLessServiceLevelIndicator;

public class MetricLessServiceLevelIndicatorTransformer
    extends ServiceLevelIndicatorTransformer<MetricLessServiceLevelIndicator, MetricLessServiceLevelIndicatorSpec> {
  @Override
  public MetricLessServiceLevelIndicator getEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      boolean isEnabled) {
    return MetricLessServiceLevelIndicator.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelIndicatorDTO.getIdentifier())
        .name(serviceLevelIndicatorDTO.getName())
        .monitoredServiceIdentifier(monitoredServiceIndicator)
        .enabled(isEnabled)
        .build();
  }

  @Override
  protected MetricLessServiceLevelIndicatorSpec getSpec(MetricLessServiceLevelIndicator serviceLevelIndicator) {
    return MetricLessServiceLevelIndicatorSpec.builder().build();
  }
}
