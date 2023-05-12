/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;

import com.google.inject.Inject;
import javax.ejb.Singleton;
import lombok.NonNull;

@Singleton
public class ServiceOverrideValidatorFactory {
  @Inject private EnvGlobalOverrideRequestParamsHandler envGlobalOverrideRequestParamsHandler;
  @Inject private EnvServiceOverrideRequestParamsHandler envServiceOverrideRequestParamsHandler;

  @Inject private InfraGlobalOverrideRequestParamsHandler infraGlobalOverrideRequestParamsHandler;
  @Inject private InfraServiceOverrideRequestParamsHandler infraServiceOverrideRequestParamsHandler;

  public ServiceOverrideTypeBasedRequestParamsHandler getTypeBasedValidator(
      @NonNull ServiceOverridesType overridesType) {
    switch (overridesType) {
      case ENV_GLOBAL_OVERRIDE:
        return envGlobalOverrideRequestParamsHandler;
      case ENV_SERVICE_OVERRIDE:
        return envServiceOverrideRequestParamsHandler;
      case INFRA_GLOBAL_OVERRIDE:
        return infraGlobalOverrideRequestParamsHandler;
      case INFRA_SERVICE_OVERRIDE:
        return infraServiceOverrideRequestParamsHandler;
      default:
        throw new InvalidRequestException(
            String.format("Validator has not been implemented for override type %s", overridesType));
    }
  }
}
