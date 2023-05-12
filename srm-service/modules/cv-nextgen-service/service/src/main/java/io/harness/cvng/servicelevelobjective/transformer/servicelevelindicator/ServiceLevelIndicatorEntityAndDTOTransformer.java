/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorUpdatableEntity;

import com.google.inject.Inject;
import java.util.Map;

public class ServiceLevelIndicatorEntityAndDTOTransformer {
  @Inject private Map<String, ServiceLevelIndicatorTransformer> serviceLevelIndicatorFQDITransformerMapBinder;

  @Inject private Map<String, ServiceLevelIndicatorUpdatableEntity> serviceLevelIndicatorMapBinder;

  public ServiceLevelIndicator getEntity(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String monitoredServiceIndicator, String healthSourceIndicator, boolean isEnabled) {
    ServiceLevelIndicatorTransformer serviceLevelIndicatorTransformer =
        serviceLevelIndicatorFQDITransformerMapBinder.get(serviceLevelIndicatorDTO.getEvaluationAndMetricType());
    return serviceLevelIndicatorTransformer.getEntity(
        projectParams, serviceLevelIndicatorDTO, monitoredServiceIndicator, healthSourceIndicator, isEnabled);
  }

  public ServiceLevelIndicatorDTO getDto(ServiceLevelIndicator serviceLevelIndicator) {
    ServiceLevelIndicatorTransformer serviceLevelIndicatorTransformer =
        serviceLevelIndicatorFQDITransformerMapBinder.get(ServiceLevelIndicator.getEvaluationAndMetricType(
            serviceLevelIndicator.getSLIEvaluationType(), serviceLevelIndicator.getSLIMetricType()));
    return serviceLevelIndicatorTransformer.getDTO(serviceLevelIndicator);
  }

  public UpdatableEntity<ServiceLevelIndicator, ServiceLevelIndicator> getUpdatableEntity(
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    return serviceLevelIndicatorMapBinder.get(serviceLevelIndicatorDTO.getEvaluationAndMetricType());
  }
}
