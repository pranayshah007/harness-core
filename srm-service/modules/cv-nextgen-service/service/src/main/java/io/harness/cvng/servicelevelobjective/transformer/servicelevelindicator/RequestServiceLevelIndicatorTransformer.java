/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slispec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator;

public class RequestServiceLevelIndicatorTransformer
    extends ServiceLevelIndicatorTransformer<RequestServiceLevelIndicator, RequestBasedServiceLevelIndicatorSpec> {
  @Override
  public RequestServiceLevelIndicator getEntity(ProjectParams projectParams,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, String monitoredServiceIndicator, String healthSourceIndicator,
      boolean isEnabled) {
    RequestBasedServiceLevelIndicatorSpec requestBasedServiceLevelIndicatorSpec =
        (RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec();
    return RequestServiceLevelIndicator.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelIndicatorDTO.getIdentifier())
        .name(serviceLevelIndicatorDTO.getName())
        .metric1(requestBasedServiceLevelIndicatorSpec.getMetric1())
        .metric2(requestBasedServiceLevelIndicatorSpec.getMetric2())
        .eventType(requestBasedServiceLevelIndicatorSpec.getEventType())
        .monitoredServiceIdentifier(monitoredServiceIndicator)
        .healthSourceIdentifier(healthSourceIndicator)
        .enabled(isEnabled)
        .build();
  }

  @Override
  protected RequestBasedServiceLevelIndicatorSpec getSpec(RequestServiceLevelIndicator serviceLevelIndicator) {
    return RequestBasedServiceLevelIndicatorSpec.builder()
        .eventType(serviceLevelIndicator.getEventType())
        .metric1(serviceLevelIndicator.getMetric1())
        .metric2(serviceLevelIndicator.getMetric2())
        .build();
  }
}
