/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.migrations.licenses;

import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.migration.NGMigration;
import io.harness.subscription.entities.SubscriptionDetail;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class ModuleLicenseToSubscriptionMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;

  private static final String ACCOUNT_ID = "accountIdentifier";
  private static final String MODULE_TYPE = "moduleType";
  private static final String SUBSCRIPTION_ID = "subscriptionId";

  @Override
  public void migrate() {
    log.info("Starting the module license migration");

    MongoCollection<Document> subscriptionDetails = mongoTemplate.getCollection("subscriptionDetails");

    // add subscriptionDetails.subscriptionId to moduleLicense
    FindIterable<Document> it = subscriptionDetails.find();
    for (Document detail : it) {
      Query fetchMatchingLicenseQuery = new Query();
      fetchMatchingLicenseQuery.addCriteria(Criteria.where(ACCOUNT_ID).is(detail.get(ACCOUNT_ID)))
          .addCriteria(Criteria.where(MODULE_TYPE).is(detail.get(MODULE_TYPE)))
          .addCriteria(Criteria.where("status").is("ACTIVE"));

      Update addSubscriptionIdUpdate = new Update();
      addSubscriptionIdUpdate.set(SUBSCRIPTION_ID, detail.get(SUBSCRIPTION_ID));

      try {
        mongoTemplate.updateFirst(fetchMatchingLicenseQuery, addSubscriptionIdUpdate, ModuleLicense.class);
      } catch (Exception ex) {
        log.info("Failed to add subscriptionDetailsId to moduleLicense with accountId {} and moduleType {}. {} {}",
            detail.get(ACCOUNT_ID), detail.get(MODULE_TYPE), ex.getMessage(), ex.getStackTrace());
      }
    }

    // remove moduleType from subscriptionDetails
    Update removeModuleTypeUpdate = new Update();
    removeModuleTypeUpdate.unset(MODULE_TYPE);

    try {
      mongoTemplate.updateMulti(new Query(), removeModuleTypeUpdate, SubscriptionDetail.class);
    } catch (Exception ex) {
      log.info("Failed to remove moduleType from subscriptionDetails collection. {} {}", ex.getMessage(),
          ex.getStackTrace());
    }

    log.info("Finished the module license migration");
  }
}