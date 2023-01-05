/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddScopedToAccountAttributeToEncryptedData implements Migration {
  private WingsPersistence wingsPersistence;

  @Inject
  public AddScopedToAccountAttributeToEncryptedData(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public void migrate() {
    log.info("Executing migration AddScopedToAccountAttributeToEncryptedData");
    UpdateOperations<EncryptedData> updateOperations = wingsPersistence.createUpdateOperations(EncryptedData.class)
                                                           .set(EncryptedDataKeys.scopedToAccount, Boolean.TRUE);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class)
            .field(EncryptedDataKeys.accountId)
            .exists()
            .field(EncryptedDataKeys.type)
            .hasAnyOf(Sets.newHashSet(SettingVariableTypes.SECRET_TEXT.name(), SettingVariableTypes.CONFIG_FILE.name()))
            .field(EncryptedDataKeys.usageRestrictions)
            .doesNotExist();

    UpdateResults result = wingsPersistence.update(query, updateOperations);

    log.info("Migration AddScopedToAccountAttributeToEncryptedData updated {} EncryptedData records",
        result.getUpdatedCount());
  }
}
