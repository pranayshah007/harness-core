/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;

import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.cdng.validations.helper.OrgAndProjectValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.OverrideCRUDRequestType;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
public class ServiceOverrideValidatorServiceImpl implements ServiceOverrideValidatorService {
  @Inject private ServiceOverrideValidatorFactory overrideValidatorFactory;
  @Inject private EnvironmentNGAccessControlCheckHelper environmentAccessControlCheckHelper;
  @Inject private EnvironmentValidationHelper environmentValidationHelper;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  @Override
  public void validateRequest(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId,
      @NonNull OverrideCRUDRequestType crudRequestType) {
    validateServiceOverrideRequestBasicChecks(requestDTOV2, accountId);
    validateEnvUsedInServiceOverrideRequest(accountId, requestDTOV2.getOrgIdentifier(),
        requestDTOV2.getProjectIdentifier(), requestDTOV2.getEnvironmentRef());
    ServiceOverrideTypeBasedRequestParamsHandler validator =
        overrideValidatorFactory.getTypeBasedValidator(requestDTOV2.getType());
    validator.validateRequest(requestDTOV2);
  }

  public void validateEnvUsedInServiceOverrideRequest(
      @NotNull String accountId, String orgId, String projectId, @NonNull String environmentRef) {
    Environment environment = checkIfEnvExistAndReturn(accountId, orgId, projectId, environmentRef);
    validateEnvironmentRBAC(environment);
  }

  public void validateServiceOverrideRequestBasicChecks(
      @NotNull ServiceOverrideRequestDTOV2 serviceOverrideRequestDTOV2, @NotNull String accountId) {
    throwExceptionRequiredFields(serviceOverrideRequestDTOV2);
    validateServiceOverrideScope(serviceOverrideRequestDTOV2);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceOverrideRequestDTOV2.getOrgIdentifier(), serviceOverrideRequestDTOV2.getProjectIdentifier(), accountId);
  }

  @Override
  public void validateEnvironmentRBAC(@NotNull Environment environment) {
    environmentAccessControlCheckHelper.checkForEnvAndAttributesAccessOrThrow(
        ResourceScope.of(
            environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier()),
        environment.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION, environment.getType().toString());
  }

  @Override
  @NonNull
  public String generateServiceOverrideIdentifier(@NotNull NGServiceOverridesEntity serviceOverridesEntity) {
    ServiceOverrideTypeBasedRequestParamsHandler validator =
        overrideValidatorFactory.getTypeBasedValidator(serviceOverridesEntity.getType());

    return validator.generateServiceOverrideIdentifier(serviceOverridesEntity);
  }

  @NonNull
  private Environment checkIfEnvExistAndReturn(
      String accountId, String orgId, String projectId, String environmentRef) {
    return environmentValidationHelper.checkThatEnvExists(accountId, orgId, projectId, environmentRef);
  }

  private void throwExceptionRequiredFields(ServiceOverrideRequestDTOV2 dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for Service overrides");
    }
    if (isEmpty(dto.getEnvironmentRef())) {
      throw new InvalidRequestException("No environment identifier provided in Service Overrides request");
    }
    if (dto.getType() == null) {
      throw new InvalidRequestException("Override type is not provided in request");
    }
    if (dto.getSpec() == null) {
      throw new InvalidRequestException("Override spec is not provided in request");
    }
  }

  private void validateServiceOverrideScope(ServiceOverrideRequestDTOV2 requestDTO) {
    if (isNotEmpty(requestDTO.getProjectIdentifier()) && isEmpty(requestDTO.getOrgIdentifier())) {
      throw new InvalidRequestException("org identifier must be specified when project identifier is specified.");
    }
  }
}
