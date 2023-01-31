/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.resources;

import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;
import io.harness.idp.secret.resource.SecretManagerResource;
import io.harness.idp.secret.service.EnvironmentVariableService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class SecretManagerResourceImpl implements SecretManagerResource {
  private EnvironmentVariableService environmentVariableService;

  public String getSecretIdByEnvName(String envName, String accountIdentifier) {
    Optional<EnvironmentVariableDTO> environmentVariableDTOOpt =
        environmentVariableService.findByEnvName(envName, accountIdentifier);
    if (environmentVariableDTOOpt.isEmpty()) {
      throw new NotFoundException("Environment Variable with name " + envName + " not found");
    }
    return environmentVariableDTOOpt.get().getSecretIdentifier();
  }
}
