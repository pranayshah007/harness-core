/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

public class EnableExistingCVConfigs implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    hPersistence.update(hPersistence.createQuery(CVConfig.class).field(CVConfigKeys.enabled).doesNotExist(),
        hPersistence.createUpdateOperations(CVConfig.class).set(CVConfigKeys.enabled, true));
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.builder()
        .desc("This is a new field that is getting added to CVConfig so rollback won't have any impact.")
        .build();
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.builder()
        .desc("This is a new field that is getting added to CVConfig so rollback won't have any impact.")
        .build();
  }
}
