/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration;

import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGMigrationStatus;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGSchemaKeys;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class CVNGSchemaHandler implements Handler<CVNGSchema> {
  @Inject private CVNGMigrationService cvngMigrationService;
  @Inject private HPersistence hPersistence;

  @Override
  public void handle(CVNGSchema entity) {
    UpdateOperations<CVNGSchema> updateOperations =
        hPersistence.createUpdateOperations(CVNGSchema.class)
            .set(CVNGSchemaKeys.cvngMigrationStatus, CVNGMigrationStatus.RUNNING);

    hPersistence.update(entity, updateOperations);

    log.info("Enqueuing CVNGSchema {}", entity.getUuid());
    cvngMigrationService.runMigrations();
    log.info("Done enqueuing CVNGSchema {}", entity.getUuid());

    updateOperations.set(CVNGSchemaKeys.cvngMigrationStatus, CVNGMigrationStatus.SUCCESS);
    hPersistence.update(entity, updateOperations);
  }
}
