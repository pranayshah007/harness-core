/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import com.google.inject.Singleton;
import lombok.NonNull;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public interface ServiceOverrideTypeBasedRequestParamsHandler {
  void validateRequest(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId);

  String generateServiceOverrideIdentifier(@NonNull NGServiceOverridesEntity serviceOverridesEntity);
}
