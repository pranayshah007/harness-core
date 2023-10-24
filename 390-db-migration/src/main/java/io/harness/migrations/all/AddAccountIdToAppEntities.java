/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Base;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.Key;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Add accountId to app child entities like service/env/provisioner/workflow/pipeline.
 *
 * @author rktummala on 02/26/19
 */
@Slf4j
public class AddAccountIdToAppEntities implements Migration {
  @Inject protected WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts = new HIterator<>(
             wingsPersistence.createAnalyticsQuery(Account.class).project(Account.ID_KEY2, true).fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();

        List<Key<Application>> appIdKeyList = wingsPersistence.createQuery(Application.class)
                                                  .filter(ApplicationKeys.accountId, account.getUuid())
                                                  .asKeyList();
        if (isNotEmpty(appIdKeyList)) {
          Set<String> appIdSet =
              appIdKeyList.stream().map(applicationKey -> (String) applicationKey.getId()).collect(Collectors.toSet());
          bulkSetAccountId(account.getUuid(), InfrastructureProvisioner.class, appIdSet);
        }
      }
    }
  }

  protected <T extends Base> void bulkSetAccountId(String accountId, Class<T> clazz, Set<String> appIdSet) {
    final DBCollection collection = wingsPersistence.getCollection(clazz);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 1;
    try (HIterator<T> entities = new HIterator<>(wingsPersistence.createQuery(clazz)
                                                     .field("accountId")
                                                     .doesNotExist()
                                                     .field("appId")
                                                     .in(appIdSet)
                                                     .project(Base.ID_KEY2, true)
                                                     .fetch())) {
      while (entities.hasNext()) {
        final T entity = entities.next();

        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("Entity:{} {} updated", clazz.getSimpleName(), i);
        }
        ++i;

        bulkWriteOperation
            .find(wingsPersistence.createQuery(clazz).filter(Base.ID_KEY2, entity.getUuid()).getQueryObject())
            .updateOne(new BasicDBObject("$set", new BasicDBObject("accountId", accountId)));
      }
    }
    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }
}
