/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.service;

import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.event.reconciliation.service.ServiceEntityReconServiceImpl;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Service;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateServicesToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class ServiceTimeScaleEntity implements TimeScaleEntity<Service> {
  @Inject private ServiceTimescaleChangeHandler serviceTimescaleChangeHandler;
  @Inject private ServiceEntityReconServiceImpl serviceEntityReconService;
  @Inject private MigrateServicesToTimeScaleDB migrateServicesToTimeScaleDB;

  public static final Class<Service> SOURCE_ENTITY_CLASS = Service.class;

  @Override
  public Class<Service> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return serviceTimescaleChangeHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return serviceEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    Service service = (Service) entity;

    return accountIds.contains(service.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateServicesToTimeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(Service entity) {}

  @Override
  public void deleteFromTimescale(String id) {}
}
