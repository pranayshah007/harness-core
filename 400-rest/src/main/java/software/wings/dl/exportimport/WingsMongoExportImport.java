/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.dl.exportimport;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;
import software.wings.dl.exportimport.ImportStatusReport.ImportStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.DBCollectionFindOptions;
import dev.morphia.annotations.Entity;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * @author marklu on 10/24/18
 */
@Slf4j
@Singleton
@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
public class WingsMongoExportImport {
  private static final int BATCH_SIZE = 1000;
  private static final String JSON_FILE_SUFFIX = ".json";
  private JsonParser jsonParser = new JsonParser();

  private Gson gson = new GsonBuilder().setPrettyPrinting().create();

  @Inject private WingsPersistence wingsPersistence;

  /**
   * Export the collection object matching the query filter into raw JSON documents.
   */
  public List<String> exportRecords(DBObject filter, String collectionName) {
    final List<String> records = new ArrayList<>();
    DBCollection collection =
        wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE).getDB().getCollection(collectionName);

    DBCursor cursor = collection.find(filter, new DBCollectionFindOptions().batchSize(BATCH_SIZE));
    while (cursor.hasNext()) {
      BasicDBObject basicDBObject = (BasicDBObject) cursor.next();
      records.add(basicDBObject.toJson());
    }

    return records;
  }

  public boolean exportRecords(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream, DBObject filter,
      String collectionName, int batchSize, int mongoBatchSize, List<String> identifiers,
      long exportRecordsUpdatedAfter, long exportRecordsCreatedAfter) throws Exception {
    if (mongoBatchSize == 0) {
      mongoBatchSize = BATCH_SIZE;
    }
    DBCollection collection =
        wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE).getDB().getCollection(collectionName);

    getUpdatedFilter(collection, filter, identifiers, exportRecordsUpdatedAfter, exportRecordsCreatedAfter);

    DBCursor cursor = collection.find(filter, new DBCollectionFindOptions().batchSize(mongoBatchSize));
    int i = 0;
    List<String> records = new ArrayList<>();

    while (cursor.hasNext()) {
      BasicDBObject basicDBObject = (BasicDBObject) cursor.next();
      records.add(basicDBObject.toJson());
      if (records.size() >= batchSize) {
        try {
          String zipEntryName = collectionName + "_" + i + JSON_FILE_SUFFIX;
          exportToStream(zipOutputStream, fileOutputStream, records, zipEntryName);
          i++;
          log.info("{} number of records of batch {} and collection {} have been exported.", records.size(), i,
              collectionName);
          records = new ArrayList<>();
        } catch (IOException ioe) {
          log.error(
              "Migration: Getting error while batch exporting for collection {} at batch {}", collectionName, i, ioe);
          throw new Exception("Migration: Issue while batch exporting for collection " + collectionName);
        }
      }
    }
    if (records.size() > 0) {
      String zipEntryName = collectionName + "_" + i + JSON_FILE_SUFFIX;
      exportToStream(zipOutputStream, fileOutputStream, records, zipEntryName);
    }
    return true;
  }

  private JsonArray convertStringListToJsonArray(List<String> records) {
    JsonArray jsonArray = new JsonArray();
    for (String record : records) {
      jsonArray.add(jsonParser.parse(record));
    }
    return jsonArray;
  }

  private void exportToStream(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream, List<String> records,
      String zipEntryName) throws IOException {
    ZipEntry zipEntryData = new ZipEntry(zipEntryName);
    log.info("Zipping entry: {}", zipEntryName);
    zipOutputStream.putNextEntry(zipEntryData);
    JsonArray jsonArrayRecord = convertStringListToJsonArray(records);
    String jsonString = gson.toJson(jsonArrayRecord);
    zipOutputStream.write(jsonString.getBytes(Charset.defaultCharset()));
    zipOutputStream.flush();
    fileOutputStream.flush();
  }

  private void getUpdatedFilter(DBCollection collection, DBObject filter, List<String> identifiers,
      long exportRecordsUpdatedAfter, long exportRecordsCreatedAfter) {
    if (EmptyPredicate.isNotEmpty(identifiers)) {
      filter.put("_id", new BasicDBObject("$in", identifiers));
    } else {
      if (exportRecordsUpdatedAfter > 0) {
        boolean lastUpdatedAtFieldExists = false;
        DBObject query = new BasicDBObject("lastUpdatedAt", new BasicDBObject("$exists", true));
        try (DBCursor existsCursor = collection.find(query)) {
          if (existsCursor.hasNext()) {
            lastUpdatedAtFieldExists = true;
          }
        }
        if (lastUpdatedAtFieldExists) {
          filter.put("lastUpdatedAt", new BasicDBObject("$gte", exportRecordsUpdatedAfter));
        } else {
          // Some records might not have lastUpdatedAt populated. In such case,
          // fall back to createdAt field
          filter.put("createdAt", new BasicDBObject("$gte", exportRecordsCreatedAfter));
        }
      } else if (exportRecordsCreatedAfter > 0) {
        boolean createdAtFieldExists = false;
        DBObject query = new BasicDBObject("createdAt", new BasicDBObject("$exists", true));
        try (DBCursor existsCursor = collection.find(query)) {
          if (existsCursor.hasNext()) {
            createdAtFieldExists = true;
          }
        }
        if (createdAtFieldExists) {
          filter.put("createdAt", new BasicDBObject("$gte", exportRecordsCreatedAfter));
        }
      }
    }
  }

  public boolean exportRecords(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream, DBObject filter,
      String collectionName, int batchNumber, int batchSize, int mongoBatchSize, List<String> identifiers,
      long exportRecordsUpdatedAfter, long exportRecordsCreatedAfter) throws Exception {
    if (mongoBatchSize == 0) {
      mongoBatchSize = BATCH_SIZE;
    }
    DBCollection collection =
        wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE).getDB().getCollection(collectionName);

    getUpdatedFilter(collection, filter, identifiers, exportRecordsUpdatedAfter, exportRecordsCreatedAfter);

    DBCursor cursor = collection.find(filter, new DBCollectionFindOptions().batchSize(mongoBatchSize));
    int i = 0;
    List<String> records = new ArrayList<>();

    while (cursor.hasNext()) {
      BasicDBObject basicDBObject = (BasicDBObject) cursor.next();
      if (i >= batchSize * batchNumber) {
        records.add(basicDBObject.toJson());
      }
      if (EmptyPredicate.isNotEmpty(records) && records.size() >= batchSize) {
        try {
          String zipEntryName = collectionName + JSON_FILE_SUFFIX;
          exportToStream(zipOutputStream, fileOutputStream, records, zipEntryName);
          log.info("{} number of records of batch {} and collection {} have been exported.", records.size(),
              batchNumber, collectionName);
          records = new ArrayList<>();
          break;
        } catch (IOException ioe) {
          log.error(
              "Migration: Getting error while batch exporting for collection {} at batch {}", collectionName, i, ioe);
          throw new Exception("Migration: Issue while batch exporting for collection " + collectionName);
        }
      }
      i++;
    }
    if (records.size() > 0) {
      String zipEntryName = collectionName + JSON_FILE_SUFFIX;
      exportToStream(zipOutputStream, fileOutputStream, records, zipEntryName);
    }
    return true;
  }

  /**
   * Import raw JSON data records into existing mongo collection using the specified import mode. If needed,
   * the 'naturalKeyFields' besides '_id' field will be used to identify if there is pre-existing record in
   * the same collection already. Depending on the import mode, different action will be taken to handle
   * pre-existing records.
   *
   * @param  mode one of the supported import mode such as DRY_RUN/UPSERT etc.
   */
  public ImportStatus importRecords(String collectionName, List<String> records, ImportMode mode) {
    DBCollection collection =
        wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE).getDB().getCollection(collectionName);

    int totalRecords = records.size();
    int importedRecords = 0;
    int idClashCount = 0;
    for (String record : records) {
      // Check for if there is any existing record with the same _id.
      DBObject importRecord = BasicDBObject.parse(record);
      Object id = importRecord.get("_id");
      long recordCountFromId = collection.getCount(new BasicDBObject("_id", id));
      idClashCount += recordCountFromId;

      switch (mode) {
        case DRY_RUN:
          break;
        case INSERT:
          if (recordCountFromId == 0) {
            // Totally new record, it can be inserted directly.
            try {
              collection.insert(importRecord, WriteConcern.ACKNOWLEDGED);
              importedRecords++;
            } catch (DuplicateKeyException e) {
              log.warn("Skip importing a record with conflicting key with existing record in db.", e);
            }
          }
          break;
        case UPSERT:
          // We should not UPSERT record if same ID record exists, but with different natural key.
          try {
            collection.save(importRecord, WriteConcern.ACKNOWLEDGED);
            importedRecords++;
          } catch (DuplicateKeyException e) {
            // PL-2536: Skip conflicting user entries when import account data exported from free into paid cluster.
            log.warn("Skip importing a record with conflicting key with existing record in db.", e);
          }
          break;
        default:
          throw new IllegalArgumentException("Import mode " + mode + " is not supported");
      }
    }

    if (importedRecords + idClashCount > 0) {
      log.info("{} '{}' records have the same ID as existing records.", idClashCount, collectionName);
      log.info("{} out of {} '{}' records have been imported successfully in {} mode.", importedRecords, totalRecords,
          collectionName, mode);
    }
    return ImportStatus.builder()
        .collectionName(collectionName)
        .imported(importedRecords)
        .idClashes(idClashCount)
        .build();
  }

  public static String getCollectionName(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotation(Entity.class).value();
  }
}
