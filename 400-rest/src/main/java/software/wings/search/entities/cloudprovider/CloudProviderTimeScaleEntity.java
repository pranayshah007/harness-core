/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.cloudprovider;

import io.harness.persistence.PersistentEntity;

import software.wings.beans.SettingAttribute;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateCloudProvidersToTimescaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class CloudProviderTimeScaleEntity implements TimeScaleEntity<SettingAttribute> {
  @Inject private CloudProviderTimescaleChangeHandler cloudProviderTimescaleChangeHandler;
  @Inject private MigrateCloudProvidersToTimescaleDB migrateCloudProvidersToTimescaleDB;

  public static final Class<SettingAttribute> SOURCE_ENTITY_CLASS = SettingAttribute.class;

  @Override
  public Class<SettingAttribute> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return cloudProviderTimescaleChangeHandler;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    SettingAttribute settingAttribute = (SettingAttribute) entity;

    return accountIds.contains(settingAttribute.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateCloudProvidersToTimescaleDB.runTimeScaleMigration(accountId);
  }
}
