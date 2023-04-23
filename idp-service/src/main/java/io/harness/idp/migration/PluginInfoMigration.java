/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.plugin.services.PluginInfoService;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class PluginInfoMigration implements NGMigration {
  @Inject PluginInfoService pluginInfoService;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding plugin details in pluginInfo collection.");
    pluginInfoService.saveAllPluginInfo();
    log.info("Migration complete for adding plugin details in pluginInfo collection.");
  }
}
