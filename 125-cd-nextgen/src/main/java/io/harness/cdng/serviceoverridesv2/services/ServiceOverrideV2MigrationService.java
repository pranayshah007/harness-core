/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;

import lombok.NonNull;

public interface ServiceOverrideV2MigrationService {
  @NonNull
  ServiceOverrideMigrationResponseDTO migrateToV2(
      @NonNull String accountId, String orgId, String projectId, boolean migrateChildren);
}
