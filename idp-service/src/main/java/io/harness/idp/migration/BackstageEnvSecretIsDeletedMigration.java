/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvSecretIsDeletedMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info(
        "Starting the migration for adding isDeleted field (default = false) in backstageEnvVariables collection.");

    BasicDBObject basicDBObject = new BasicDBObject();
    BasicDBObject updateOps = new BasicDBObject(BackstageEnvSecretVariableKeys.isDeleted, false);
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(BackstageEnvVariableEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Added isDeleted field successfully for {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not add isDeleted field to any record");
    }

    log.info("Migration complete for adding isDeleted field (default = false) in backstageEnvVariables collection.");
  }
}
