/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentValidationHelper {
  @Inject private EnvironmentService environmentService;

  public boolean checkThatEnvExists(@NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier) {
    Optional<Environment> environment =
        environmentService.get(accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, false);
    if (!environment.isPresent()) {
      throw new NotFoundException(String.format("environment [%s] not found.", envIdentifier));
    }
    return true;
  }
}
