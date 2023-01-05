/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataDogLogCvConfigMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Override
  public void migrate() {
    UpdateOperations<CVConfiguration> updateOperations =
        wingsPersistence.createUpdateOperations(CVConfiguration.class)
            .disableValidation()
            .set("className", "software.wings.verification.datadog.DatadogLogCVConfiguration");
    Query<CVConfiguration> query = wingsPersistence.createQuery(CVConfiguration.class)
                                       .filter(CVConfigurationKeys.stateType, StateType.DATA_DOG_LOG);
    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);
    log.info("Updated cvConfig Id with DATA_DOG_LOG: {} ", updateResults);
  }
}
