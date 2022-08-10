/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.application;

import io.harness.event.reconciliation.service.ApplicationEntityReconServiceImpl;
import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Application;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateApplicationsToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class ApplicationTimeScaleEntity implements TimeScaleEntity<Application> {
  @Inject private ApplicationTimescaleChangeHandler applicationTimescaleChangeHandler;
  @Inject private ApplicationEntityReconServiceImpl applicationEntityReconService;
  @Inject private MigrateApplicationsToTimeScaleDB migrateApplicationsToTimeScaleDB;
  public static final Class<Application> SOURCE_ENTITY_CLASS = Application.class;

  @Override
  public Class<Application> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return applicationTimescaleChangeHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return applicationEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    Application application = (Application) entity;
    return accountIds.contains(application.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateApplicationsToTimeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(Application entity) {
    migrateApplicationsToTimeScaleDB.saveToTimeScale(entity);
  }

  @Override
  public void deleteFromTimescale(String id) {
    migrateApplicationsToTimeScaleDB.deleteFromTimescale(id);
  }
}
