/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import groovy.util.logging.Slf4j;

@Slf4j
public class DeleteSLISLOMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    hPersistence.deleteOnServer(hPersistence.createQuery(ServiceLevelIndicator.class));
    hPersistence.deleteOnServer(hPersistence.createQuery(ServiceLevelObjective.class));
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
