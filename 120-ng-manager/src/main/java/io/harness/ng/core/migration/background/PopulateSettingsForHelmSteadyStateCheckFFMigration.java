/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.ngsettings.SettingCategory.CD;
import static io.harness.ngsettings.SettingValueType.BOOLEAN;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.migration.NGMigration;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.entities.Setting;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PopulateSettingsForHelmSteadyStateCheckFFMigration implements NGMigration {
  @Inject private NGFeatureFlagHelperService featureFlagService;
  @Inject private SettingRepository settingRepository;
  private static final String DEBUG_LOG = "[PopulateSettingsForHelmSteadyStateCheckFFMigration]: ";

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Start migrating HELM_STEADY_STATE_CHECK_1_16 FF enabled accounts to account level settings");
    String settingIdentifier = SettingIdentifiers.ENABLE_STEADY_STATE_FOR_JOBS_KEY_IDENTIFIER;
    String settingTrueValue = "true";
    AtomicInteger iterationCounter = new AtomicInteger();
    try {
      Set<String> accountIds =
          featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.HELM_STEADY_STATE_CHECK_1_16.name());
      accountIds.forEach(accountId -> {
        Setting setting = Setting.builder()
                              .accountIdentifier(accountId)
                              .identifier(settingIdentifier)
                              .allowOverrides(true)
                              .category(CD)
                              .value(settingTrueValue)
                              .valueType(BOOLEAN)
                              .build();
        settingRepository.upsert(setting);
        iterationCounter.addAndGet(1);
      });
    } catch (Exception e) {
      log.error(
          format("%s Migration has failed. Iterated through %s entries, Error occurred while migrating the setting.",
              DEBUG_LOG, iterationCounter),
          e);
    }
    log.info(format("%s Migration was successful. Iterated through %s entries.", DEBUG_LOG, iterationCounter));
  }
}
