/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.environment;

import io.harness.event.reconciliation.service.EnvironmentEntityReconServiceImpl;
import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Environment;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateEnvironmentsToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class EnvironmentTimeScaleEntity implements TimeScaleEntity<Environment> {
  @Inject private EnvironmentTimescaleChangeHandler environmentTimescaleChangeHandler;
  @Inject private EnvironmentEntityReconServiceImpl environmentEntityReconService;
  @Inject private MigrateEnvironmentsToTimeScaleDB migrateEnvironmentsToTimeScaleDB;

  public static final Class<Environment> SOURCE_ENTITY_CLASS = Environment.class;

  @Override
  public Class<Environment> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return environmentTimescaleChangeHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return environmentEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    Environment environment = (Environment) entity;

    return accountIds.contains(environment.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateEnvironmentsToTimeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(Environment entity) {}

  @Override
  public void deleteFromTimescale(String id) {}
}
