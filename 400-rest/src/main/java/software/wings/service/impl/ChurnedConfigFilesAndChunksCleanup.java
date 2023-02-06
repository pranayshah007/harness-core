/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.ANALYTIC_STORE;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

@Slf4j
public class ChurnedConfigFilesAndChunksCleanup {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ChurnedAccountDeletionHelper churnedAccountDeletionHelper;
  private static final int batchSize = 3000;

  public void deleteConfigFilesAndChunks(String accountId) {
    log.info("Start: Deleting config files and chunks for the churned account {}", accountId);
    DBCollection configCollection = wingsPersistence.getCollection(ANALYTIC_STORE, "configFiles");
    DBCollection configFilesCollection = wingsPersistence.getCollection(DEFAULT_STORE, "configs.files");
    DBCollection configFileAnalytics = wingsPersistence.getCollection(ANALYTIC_STORE, "configs.files");
    DBCollection configChunksCollection = wingsPersistence.getCollection(DEFAULT_STORE, "configs.chunks");
    BasicDBObject matchCondition = new BasicDBObject("accountId", accountId);
    BasicDBObject projection = new BasicDBObject("_id", true);

    try (DBCursor configRecords = configCollection.find(matchCondition, projection).batchSize(batchSize)) {
      while (true) {
        List<String> configIds = new ArrayList<>();
        while (configRecords.hasNext()) {
          DBObject record = configRecords.next();
          configIds.add(record.get("_id").toString());
        }
        if (isNotEmpty(configIds)) {
          List<ObjectId> configFileIds = churnedAccountDeletionHelper.getFileIdsMatchingParentEntity(
              configIds, configFileAnalytics, "metadata.entityId");

          // Deleting the config files if they exist
          configFilesCollection.remove(new BasicDBObject("_id", new BasicDBObject("$in", configFileIds.toArray())));

          // Deleting the config chunks if they exist
          configChunksCollection.remove(
              new BasicDBObject("files_id", new BasicDBObject("$in", configFileIds.toArray())));
        } else {
          log.info("Config Files and Chunks deleted for churned account: {}", accountId);
          break;
        }
      }
    } catch (Exception e) {
      log.error("Config Files and Chunks deletion failed for churned account: {}", accountId, e);
    }
  }
}
