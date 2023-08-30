/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class UserEventEntityUserGroupIdentifierMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info(
        "Starting the migration for adding userGroupIdentifier field (default = \"\") and setting hasEvent field to false in userEvents collection.");

    BasicDBObject basicDBObject = new BasicDBObject();
    BasicDBObject updateOps = new BasicDBObject(UserEventEntity.UserEventKeys.userGroupIdentifier, "")
                                  .append(UserEventEntity.UserEventKeys.hasEvent, false);
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(UserEventEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Added userGroupIdentifier field and set hasEvent field to false successfully for {} records",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not add userGroupIdentifier field and set hasEvent field to false for any record");
    }

    try {
      mongoPersistence.getCollection(UserEventEntity.class).dropIndex("accountIdentifier_1");
      log.info("Dropped accountIdentifier_1 index on idp-harness.userEvents collection");
    } catch (Exception ex) {
      log.error("Error dropping accountIdentifier_1 index on idp-harness.userEvents collection. Error = {}",
          ex.getMessage(), ex);
    }

    log.info(
        "Migration complete for adding userGroupIdentifier field (default = \"\") and setting hasEvent field to false in userEvents collection.");
  }
}
