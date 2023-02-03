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
public class ChurnedAuditFilesAndChunksCleanup {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ChurnedAccountDeletionHelper churnedAccountDeletionHelper;
  private static final int batchSize = 3000;
  public void deleteAuditFilesAndChunks(String accountId) {
    log.info("Start: Deleting audit files and chunks for the churned account {}", accountId);
    DBCollection auditHeadersCollection = wingsPersistence.getCollection(ANALYTIC_STORE, "audits");
    DBCollection auditFileCollection = wingsPersistence.getCollection(DEFAULT_STORE, "audits.files");
    DBCollection auditFileAnalytics = wingsPersistence.getCollection(ANALYTIC_STORE, "audits.files");
    DBCollection auditChunksCollection = wingsPersistence.getCollection(DEFAULT_STORE, "audits.chunks");
    BasicDBObject matchCondition = new BasicDBObject("accountId", accountId);
    BasicDBObject projection = new BasicDBObject("_id", true);

    try (DBCursor auditHeaderRecords = auditHeadersCollection.find(matchCondition, projection).batchSize(batchSize)) {
      while (true) {
        List<String> headerIds = new ArrayList<>();
        while (auditHeaderRecords.hasNext()) {
          DBObject record = auditHeaderRecords.next();
          headerIds.add(record.get("_id").toString());
        }
        if (isNotEmpty(headerIds)) {
          List<ObjectId> auditFilesIds = churnedAccountDeletionHelper.getFileIdsMatchingParentEntity(
              headerIds, auditFileAnalytics, "metadata.headerId");

          // Deleting the audit files if they exist
          auditFileCollection.remove(new BasicDBObject("_id", new BasicDBObject("$in", auditFilesIds.toArray())));

          // Deleting the chunks if they exist
          auditChunksCollection.remove(
              new BasicDBObject("files_id", new BasicDBObject("$in", auditFilesIds.toArray())));
        } else {
          log.info("Audit Files and Chunks deleted for churned account: {}", accountId);
          break;
        }
      }
    } catch (Exception e) {
      log.error("Audit Files and Chunks deletion failed for churned account: {}", accountId, e);
    }
  }
}
