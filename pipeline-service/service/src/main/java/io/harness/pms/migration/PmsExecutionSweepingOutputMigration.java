/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.mongodb.MongoException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
@Slf4j
// We are deleting the index as validUntil currently is created as compound index and not ttl, thus after deletion, if
// any pod comes up, then ttl index would be created automatically
public class PmsExecutionSweepingOutputMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    try {
      mongoTemplate.getCollection("executionSweepingOutput").dropIndex("validUntil_1");
    } catch (MongoException e) {
      if (e.getCode() == 27) {
        log.info("validUntil_1 is already deleted for ExecutionSweepingOutput", e);
      } else {
        throw new UnexpectedException("Migration failed. Could not delete validUntil_1 for ExecutionSweepingOutput", e);
      }
    }
  }
}
