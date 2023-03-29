/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.mapping.Mapper;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class AccountDataRetentionService {
  public static final int BATCH = 1024;
  private static final long ACCEPTABLE_DIFFERENCE_IN_TIMESTAMP = 60000;

  @Inject private HPersistence persistence;

  public <T extends AccountDataRetentionEntity> int corectValidUntilAccount(
      Class<? extends AccountDataRetentionEntity> clz, Map<String, Long> accounts, long now, long assureTo) {
    log.info("Account data Retention for {} from {} assuredTo {}", clz.getName(), now, assureTo);
    Query<T> query = persistence.createQuery((Class<T>) clz, excludeAuthority);

    query.or(query.criteria(AccountDataRetentionEntity.VALID_UNTIL_KEY).equal(null),
        query.criteria(AccountDataRetentionEntity.VALID_UNTIL_KEY).lessThan(new Date(assureTo)));
    query.order(Sort.ascending(AccountDataRetentionEntity.VALID_UNTIL_KEY));

    query.project(AccountDataRetentionEntity.ACCOUNT_ID_KEY, true);
    query.project(AccountDataRetentionEntity.VALID_UNTIL_KEY, true);
    query.project(AccountDataRetentionEntity.CREATED_AT_KEY, true);

    final DBCollection collection = persistence.getCollection(clz);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 0;
    try (HIterator<T> entities = new HIterator<>(query.fetch())) {
      for (T entity : entities) {
        Long accountDataRetention = accounts.get(entity.getAccountId());
        // We do not have data retention specified for this account
        if (accountDataRetention == null) {
          continue;
        }

        long validUntil = (entity.getCreatedAt() == 0 ? now : entity.getCreatedAt()) + accountDataRetention;
        // The valid until value
        if (entity.getValidUntil() != null) {
          // If too far in the future, lets leave for later
          if (entity.getValidUntil().getTime() > assureTo) {
            break;
          }
          // If the calculated valid until and the set valid until have an acceptable difference it is fine. Already
          // correct, don't bother.
          if (Math.abs(entity.getValidUntil().getTime() - validUntil) <= ACCEPTABLE_DIFFERENCE_IN_TIMESTAMP) {
            continue;
          }
        }

        BasicDBObject set = new BasicDBObject(
            "$set", new BasicDBObject(AccountDataRetentionEntity.VALID_UNTIL_KEY, new Date(validUntil)));
        bulkWriteOperation.find(new BasicDBObject(Mapper.ID_KEY, entity.getUuid())).updateOne(set);

        if (++i % BATCH == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("{}: {} updated", clz.getName(), i);
        }
      }
    }

    if (i % BATCH > 0) {
      bulkWriteOperation.execute();
    }

    return i;
  }
}
