/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;

import software.wings.beans.ApiKeyEntry;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ApiKeyService;

import com.google.inject.Inject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 08/23/19
 *
 */
@Slf4j
public class ApiKeysSetNameMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;
  @Inject ApiKeyService apiKeyService;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(ApiKeyEntry.class);
    DBCursor apiKeys = collection.find();

    log.info("will go through " + apiKeys.size() + " api keys");

    while (apiKeys.hasNext()) {
      DBObject next = apiKeys.next();

      String uuId = (String) next.get("_id");
      String name = (String) next.get("name");
      String accountId = (String) next.get("accountId");
      if (isNotEmpty(name)) {
        continue;
      }

      try {
        ApiKeyEntry apiKeyEntry = apiKeyService.get(uuId, accountId);
        UpdateOperations<ApiKeyEntry> operations = wingsPersistence.createUpdateOperations(ApiKeyEntry.class);
        setUnset(operations, "name", apiKeyEntry.getDecryptedKey().substring(0, 5));
        wingsPersistence.update(wingsPersistence.createQuery(ApiKeyEntry.class).filter("_id", uuId).get(), operations);
        log.info("updated api key: {}", uuId);
      } catch (Exception ex) {
        log.warn("Failed to update api key: {}", uuId, ex);
      }
    }

    log.info("Completed setting name to api keys");
  }
}
