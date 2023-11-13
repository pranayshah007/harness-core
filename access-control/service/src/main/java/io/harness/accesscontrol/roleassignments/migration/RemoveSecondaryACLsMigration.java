/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.acl.persistence.ACL.SECONDARY_COLLECTION;

import io.harness.aggregator.models.MongoReconciliationOffset;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class RemoveSecondaryACLsMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    try {
      if (mongoTemplate.collectionExists(SECONDARY_COLLECTION)) {
        mongoTemplate.dropCollection(SECONDARY_COLLECTION);
      }
      if (mongoTemplate.collectionExists(MongoReconciliationOffset.SECONDARY_COLLECTION)) {
        mongoTemplate.dropCollection(MongoReconciliationOffset.SECONDARY_COLLECTION);
      }
    } catch (Exception ex) {
      log.error("Dropping secondary ACL collections failed", ex);
    }
  }
}
