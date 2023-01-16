/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.NGMigration;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.entities.Setting;
import io.harness.repositories.ngsettings.spring.SettingRepository;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NGWebhookMendateSettingsCategoryUpdateMigration implements NGMigration {
  @Inject SettingRepository settingRepository;
  @Override
  public void migrate() {
    List<String> settingIdentifiers =
        List.of("mandate_webhook_secrets_for_github_triggers", "mandate_custom_webhook_authorization");
    for (String settingIdentifier : settingIdentifiers) {
      try {
        List<Setting> settings = settingRepository.findByIdentifier(settingIdentifier);
        settings.forEach(setting -> {
          setting.setCategory(SettingCategory.PMS);
          settingRepository.save(setting);
        });
        log.info("[Migration Succeeded]: Migration complete for settingCategory for the setting with identifier {}",
            settingIdentifier);
      } catch (Exception ex) {
        log.error("[Migration Failed]: Could not migrate the SettingCategory for the setting with identifier, {}",
            settingIdentifier, ex);
      }
    }
  }
}
