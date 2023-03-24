/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

public abstract class ServiceLevelIndicatorTransformer<E extends ServiceLevelIndicator, S
                                                           extends ServiceLevelIndicatorSpec> {
  public abstract E getEntity(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String monitoredServiceIndicator, String healthSourceIndicator, boolean isEnabled);

  public final ServiceLevelIndicatorDTO getDTO(E serviceLevelIndicator) {
    return ServiceLevelIndicatorDTO.builder()
        .type(serviceLevelIndicator.getSLIExecutionType())
        .spec(getSpec(serviceLevelIndicator))
        .name(serviceLevelIndicator.getName())
        .identifier(serviceLevelIndicator.getIdentifier())
        .build();
  }

  protected abstract S getSpec(E serviceLevelIndicator);
}
