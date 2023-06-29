/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.utils.ConfigType;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface AppConfigRepositoryCustom {
  AppConfigEntity updateConfig(AppConfigEntity appConfigEntity, ConfigType configType);

  AppConfigEntity updateConfigEnablement(
      String accountIdentifier, String configId, Boolean isEnabled, ConfigType configType);

  List<AppConfigEntity> deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(long baseTimeStamp);
}
