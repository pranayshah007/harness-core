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
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

import com.google.inject.Inject;
import java.util.Map;

public class ServiceLevelIndicatorEntityAndDTOTransformer {
  @Inject private Map<SLIMetricType, ServiceLevelIndicatorTransformer> serviceLevelIndicatorTransformerMap;

  public ServiceLevelIndicator getEntity(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String monitoredServiceIndicator, String healthSourceIndicator, boolean isEnabled) {
    ServiceLevelIndicatorTransformer serviceLevelIndicatorTransformer =
        serviceLevelIndicatorTransformerMap.get(serviceLevelIndicatorDTO.getSpec().getType());
    return serviceLevelIndicatorTransformer.getEntity(
        projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator, isEnabled);
  }

  public ServiceLevelIndicatorDTO getDto(ServiceLevelIndicator serviceLevelIndicator) {
    ServiceLevelIndicatorTransformer serviceLevelIndicatorTransformer =
        serviceLevelIndicatorTransformerMap.get(serviceLevelIndicator.getSLIMetricType());
    return serviceLevelIndicatorTransformer.getDTO(serviceLevelIndicator);
  }
}
