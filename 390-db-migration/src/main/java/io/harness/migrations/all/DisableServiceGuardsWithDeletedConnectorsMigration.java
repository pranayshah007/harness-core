/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.SettingAttribute;
import software.wings.dl.WingsPersistence;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisableServiceGuardsWithDeletedConnectorsMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("starting migration");
    try (HIterator<CVConfiguration> cvConfigurations =
             new HIterator<>(wingsPersistence.createQuery(CVConfiguration.class, excludeAuthority).fetch())) {
      while (cvConfigurations.hasNext()) {
        CVConfiguration cvConfiguration = cvConfigurations.next();
        final String connectorId = cvConfiguration.getConnectorId();
        final SettingAttribute settingAttribute = wingsPersistence.get(SettingAttribute.class, connectorId);

        if (settingAttribute == null) {
          log.info("for {} in account {} the ConnectorDisconnectHandler has been deleted. Disabling the service guard",
              cvConfiguration.getUuid(), cvConfiguration.getAccountId());
          wingsPersistence.updateField(
              CVConfiguration.class, cvConfiguration.getUuid(), CVConfigurationKeys.enabled24x7, Boolean.FALSE);
          sleep(ofMillis(100));
        }
      }
    }

    log.info("migration finished");
  }
}
