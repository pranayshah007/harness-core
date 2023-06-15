/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration;

import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CVNGSchemaHandler implements Handler<CVNGSchema> {
  @Inject private CVNGMigrationService cvngMigrationService;
  @Inject private HPersistence hPersistence;

  @Override
  public void handle(CVNGSchema entity) {
    cvngMigrationService.runMigrations();
  }
}
