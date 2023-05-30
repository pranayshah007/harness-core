/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.NotFoundException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentValidationHelper {
  @Inject private EnvironmentService environmentService;

  @NonNull
  public Environment checkThatEnvExists(@NotEmpty String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotEmpty String environmentRef) {
    checkArgument(isNotEmpty(accountIdentifier), "accountId must be present");

    Optional<Environment> environment;
    String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    if (envRefSplit == null || envRefSplit.length == 1) {
      environment = environmentService.get(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, false);
    } else {
      // env ref for org/account level entity
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          environmentRef, accountIdentifier, orgIdentifier, projectIdentifier, YAMLFieldNameConstants.ENVIRONMENT);
      environment = environmentService.get(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
          envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), false);
    }

    if (environment.isEmpty()) {
      throw new NotFoundException(String.format("Environment with ref [%s] not found", environmentRef));
    }
    return environment.get();
  }
}
