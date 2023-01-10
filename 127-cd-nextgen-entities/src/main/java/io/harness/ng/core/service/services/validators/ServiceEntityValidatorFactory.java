/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.validators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.service.entity.ServiceEntity;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDP)
public class ServiceEntityValidatorFactory {
  @Inject TasServiceEntityValidator tasServiceEntityValidator;
  @Inject NoOpServiceEntityValidator noOpServiceEntityValidator;

  public ServiceEntityValidator getServiceEntityValidator(@NotNull @Valid ServiceEntity serviceEntity) {
    if (ServiceDefinitionType.TAS.equals(serviceEntity.getType())) {
      return tasServiceEntityValidator;
    }
    return noOpServiceEntityValidator;
  }
}
