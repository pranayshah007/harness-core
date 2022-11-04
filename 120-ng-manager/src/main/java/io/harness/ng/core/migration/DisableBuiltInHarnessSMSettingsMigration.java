/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.ngsettings.SettingCategory.CORE;
import static io.harness.ngsettings.SettingValueType.BOOLEAN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.migration.NGMigration;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.entities.Setting;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class DisableBuiltInHarnessSMSettingsMigration implements NGMigration {
  @Inject NGFeatureFlagHelperService featureFlagService;
  @Inject NGAccountSettingService accountSettingService;
  @Inject SettingRepository settingRepository;

  @Override
  public void migrate() {
    String settingIdentifier = SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER;
    String settingTrueValue = "true";
    try {
      Set<String> accountIds =
          featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.DISABLE_HARNESS_SM.name());
      accountIds.forEach(accountId -> {
        if (accountSettingService.getIsBuiltInSMDisabled(accountId, null, null, AccountSettingType.CONNECTOR)) {
          Setting setting = Setting.builder()
                                .accountIdentifier(accountId)
                                .identifier(settingIdentifier)
                                .allowOverrides(false)
                                .category(CORE)
                                .value(settingTrueValue)
                                .valueType(BOOLEAN)
                                .build();

          settingRepository.upsert(setting);
        }
      });
    } catch (Exception e) {
      log.error(String.format("Error occurred while migrating the setting- %s", settingIdentifier), e);
    }
  }
}
