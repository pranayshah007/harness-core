/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.CDCEntity;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
public class ChangeDataCaptureBulkSyncTask {
  @Inject private ChangeDataCaptureBulkMigrationHelper changeDataCaptureBulkMigrationHelper;
  @Inject private Set<CDCEntity<?>> cdcEntities;

  public void run() {
    changeDataCaptureBulkMigrationHelper.doBulkSync(cdcEntities);
  }
}
