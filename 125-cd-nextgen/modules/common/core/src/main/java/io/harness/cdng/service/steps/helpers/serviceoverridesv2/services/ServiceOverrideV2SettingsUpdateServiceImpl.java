/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;

import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSettingsUpdateResponseDTO;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceOverrideV2SettingsUpdateServiceImpl implements ServiceOverrideV2SettingsUpdateService {
  @Override
  public ServiceOverrideSettingsUpdateResponseDTO settingsUpdateToV2(
      @NonNull String accountId, String orgId, String projectId, boolean migrateChildren, boolean isRevert) {
    return ServiceOverrideSettingsUpdateResponseDTO.builder().build();
  }
}
