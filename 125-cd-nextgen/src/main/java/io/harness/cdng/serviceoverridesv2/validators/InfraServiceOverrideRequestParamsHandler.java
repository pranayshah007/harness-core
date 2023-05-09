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
import io.harness.beans.IdentifierRef;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class InfraServiceOverrideRequestParamsHandler implements ServiceOverrideTypeBasedRequestParamsHandler {
  @Inject OverrideV2AccessControlCheckHelper overrideV2AccessControlCheckHelper;
  @Inject private ServiceEntityValidationHelper serviceEntityValidationHelper;
  @Inject private InfrastructureEntityService infrastructureEntityService;

  @Override
  public void validateRequest(@NotNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId) {
    validateRequiredField(requestDTOV2.getServiceRef(), requestDTOV2.getInfraIdentifier());
    checkIfServiceAndInfraExist(requestDTOV2, accountId);
    overrideV2AccessControlCheckHelper.validateRBACForService(requestDTOV2, accountId);
  }

  private void checkIfServiceAndInfraExist(ServiceOverrideRequestDTOV2 requestDTOV2, String accountId) {
    serviceEntityValidationHelper.checkThatServiceExists(
        accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(), requestDTOV2.getServiceRef());

    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(requestDTOV2.getEnvironmentRef(), accountId,
        requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier());
    Optional<InfrastructureEntity> infrastructureEntity = infrastructureEntityService.get(
        envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
        envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), requestDTOV2.getInfraIdentifier());
    if (infrastructureEntity.isEmpty()) {
      throw new NotFoundException(String.format("Infrastructure entity [%s], does not exist in Environment [%s].",
          requestDTOV2.getInfraIdentifier(), envIdentifierRef.getIdentifier()));
    }
  }

  @Override
  public String generateServiceOverrideIdentifier(NGServiceOverridesEntity serviceOverridesEntity) {
    return String
        .join("_", serviceOverridesEntity.getEnvironmentRef(), serviceOverridesEntity.getServiceRef(),
            serviceOverridesEntity.getInfraIdentifier())
        .replace(".", "_");
  }

  private void validateRequiredField(String serviceRef, String infraIdentifier) {
    if (isEmpty(infraIdentifier)) {
      throw new InvalidRequestException("Infra Identifier should not be empty for INFRA-SERVICE override");
    }
    if (isEmpty(serviceRef)) {
      throw new InvalidRequestException("ServiceRef should not be empty for INFRA-SERVICE override");
    }
  }
}
