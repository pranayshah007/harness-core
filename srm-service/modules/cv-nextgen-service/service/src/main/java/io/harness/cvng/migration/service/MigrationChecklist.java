/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.service;

import io.harness.cvng.migration.beans.ChecklistItem;

public interface MigrationChecklist {
  /**
   * This is a checklist item to ensure we think about rollback while writing migration.
   * Explain what happens if the version after the migration is rolled back to previous version.
   */
  ChecklistItem whatHappensOnRollback();

  /**
   * In QA and production 2 versions can run together during deployment and after migration old version of code might
   * still be running. Think about if old code picks up the migrated entity will anything break and vise versa.
   */
  ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity();
}
