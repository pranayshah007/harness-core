/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;

import javax.ejb.Singleton;
import lombok.NonNull;

@Singleton
public class ServiceOverrideValidatorFactory {
  public ServiceOverrideTypeBasedRequestParamsHandler getTypeBasedValidator(
      @NonNull ServiceOverridesType overridesType) {
    switch (overridesType) {
      case ENV_GLOBAL_OVERRIDE:
        return new EnvGlobalOverrideRequestParamsHandler();
      case ENV_SERVICE_OVERRIDE:
        return new EnvServiceOverrideRequestParamsHandler();
      case INFRA_GLOBAL_OVERRIDE:
        return new InfraGlobalOverrideRequestParamsHandler();
      case INFRA_SERVICE_OVERRIDE:
        return new InfraServiceOverrideRequestParamsHandler();
      default:
        throw new InvalidRequestException(
            String.format("Validator has not been implemented for override type %s", overridesType));
    }
  }
}
