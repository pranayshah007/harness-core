/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.OverrideCRUDRequestType;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

public interface ServiceOverrideValidatorService {
  void validateRequest(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId,
      @NonNull OverrideCRUDRequestType crudRequestType);

  @NonNull String generateServiceOverrideIdentifier(@NonNull NGServiceOverridesEntity serviceOverridesEntity);

  void validateServiceOverrideRequestBasicChecks(
      @NonNull ServiceOverrideRequestDTOV2 serviceOverrideRequestDTOV2, @NonNull String accountId);

  void validateEnvironmentRBAC(@NonNull Environment environment);

  void validateEnvUsedInServiceOverrideRequest(
      @NotNull String accountId, String orgId, String projectId, String environmentRef);
}
