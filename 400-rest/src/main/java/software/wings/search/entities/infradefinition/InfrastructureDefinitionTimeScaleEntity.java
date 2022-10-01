/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.infradefinition;

import io.harness.persistence.PersistentEntity;

import software.wings.infra.InfrastructureDefinition;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateInfraDefinitionToTimescaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class InfrastructureDefinitionTimeScaleEntity implements TimeScaleEntity<InfrastructureDefinition> {
  @Inject private InfrastructureDefinitonTimescaleChangeHandler infrastructureDefinitonTimescaleChangeHandler;
  @Inject private MigrateInfraDefinitionToTimescaleDB migrateInfraDefinitionToTimescaleDB;

  public static final Class<InfrastructureDefinition> SOURCE_ENTITY_CLASS = InfrastructureDefinition.class;

  @Override
  public Class<InfrastructureDefinition> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return infrastructureDefinitonTimescaleChangeHandler;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entity;

    return accountIds.contains(infrastructureDefinition.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateInfraDefinitionToTimescaleDB.runTimeScaleMigration(accountId);
  }
}
