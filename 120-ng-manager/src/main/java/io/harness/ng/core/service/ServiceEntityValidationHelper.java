/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
public class ServiceEntityValidationHelper {
  @Inject private ServiceEntityService serviceEntityService;

  public void checkThatServiceExists(@NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String serviceIdentifier) {
    Optional<ServiceEntity> service =
        serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (!service.isPresent()) {
      throw new NotFoundException(String.format("service [%s] not found.", serviceIdentifier));
    }
  }
}
