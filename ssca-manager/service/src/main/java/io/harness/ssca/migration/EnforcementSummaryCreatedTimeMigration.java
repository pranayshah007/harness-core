/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class EnforcementSummaryCreatedTimeMigration implements NGMigration {
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Inject MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    log.info("Starting Enforcement Summary Entity Migration");
    long currentTime = Instant.now().toEpochMilli();
    Criteria criteria = Criteria.where(EnforcementSummaryEntityKeys.createdAt).is(null);
    Query query = new Query(criteria);
    FindIterable<Document> iterable =
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(EnforcementSummaryEntity.class))
            .find(query.getQueryObject());

    for (Document document : iterable) {
      try {
        EnforcementSummaryEntity summaryEntity =
            mongoTemplate.getConverter().read(EnforcementSummaryEntity.class, document);
        summaryEntity.setCreatedAt(currentTime);
        enforcementSummaryRepo.save(summaryEntity);
      } catch (Exception e) {
        log.error(String.format(
            "Skipping Migration for Enforcement Summary {id: %s}, {Exception: %s}", document.get("_id").toString(), e));
      }
    }
    log.info("Enforcement Summary Entity Migration Successful");
  }
}
