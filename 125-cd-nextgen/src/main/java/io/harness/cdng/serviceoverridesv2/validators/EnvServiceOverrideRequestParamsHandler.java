/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import com.google.inject.Inject;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class EnvServiceOverrideRequestParamsHandler implements ServiceOverrideTypeBasedRequestParamsHandler {
  @Inject OverrideV2AccessControlCheckHelper overrideV2AccessControlCheckHelper;
  @Inject private ServiceEntityValidationHelper serviceEntityValidationHelper;
  @Override
  public void validateRequest(@NotNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId) {
    validateRequiredField(requestDTOV2.getServiceRef());
    checkIfServiceExist(requestDTOV2, accountId);
    overrideV2AccessControlCheckHelper.validateRBACForService(requestDTOV2, accountId);
  }

  private void checkIfServiceExist(ServiceOverrideRequestDTOV2 requestDTOV2, String accountId) {
    serviceEntityValidationHelper.checkThatServiceExists(
        accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(), requestDTOV2.getServiceRef());
  }

  private void validateRequiredField(String serviceRef) {
    if (isEmpty(serviceRef)) {
      throw new InvalidRequestException("ServiceRef should not be empty for ENVIRONMENT-SERVICE override");
    }
  }

  @Override
  public String generateServiceOverrideIdentifier(NGServiceOverridesEntity serviceOverridesEntity) {
    return String.join("_", serviceOverridesEntity.getEnvironmentRef(), serviceOverridesEntity.getServiceRef())
        .replace(".", "_");
  }
}
