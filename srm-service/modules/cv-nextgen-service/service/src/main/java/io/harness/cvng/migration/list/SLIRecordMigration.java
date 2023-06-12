/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.SRMPersistence;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGSchemaKeys;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIRecordMigration implements CVNGMigration {
  @Inject SRMPersistence hPersistence;

  private static final int BATCH_SIZE = 1000;

  List<SLIRecordBucket> sliRecordBucketsToSave;

  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void migrate() {
    try {
      CVNGSchema cvngSchema = hPersistence.createQuery(CVNGSchema.class).get();
      if (cvngSchema.getObject() == null) {
        addSLIIdsInCVNGSchemaObject();
      }
      List<String> sliIds = getSLIIdsFromSchemaObject();
      while (!sliIds.isEmpty()) {
        Query<SLIRecord> queryToAddToBucket = hPersistence.createQuery(SLIRecord.class)
                                                  .filter(SLIRecordKeys.sliId, sliIds.get(0))
                                                  .order(Sort.ascending(SLIRecordKeys.timestamp));
        List<SLIRecord> currentBucket = new ArrayList<>();
        sliRecordBucketsToSave = new ArrayList<>();
        int offset = 0;
        while (true) {
          Query<SLIRecord> query = queryToAddToBucket.offset(offset).limit(BATCH_SIZE);
          List<SLIRecord> records = query.find().toList();
          if (records.isEmpty()) {
            break;
          }
          for (SLIRecord sliRecord : records) {
            currentBucket = processRecord(currentBucket, sliRecord);
            currentBucket = processBucket(currentBucket);
          }
          offset += BATCH_SIZE;
        }
        saveBuckets();
        sliIds.remove(0);
        updateSLIIdsInSchemaObject(sliIds);
      }
      log.info("[SLI Bucket Migration] Saved all sli bucket records");
    } catch (Exception exception) {
      log.error("[SLI Bucket Migration] Failed to migrate SLI Records {}", exception);
    }
  }

  private void addSLIIdsInCVNGSchemaObject() throws JsonProcessingException {
    List<String> sliIds = fetchAllSLIIds();
    String jsonString = objectMapper.writeValueAsString(sliIds);
    final UpdateOperations<CVNGSchema> updateOperations = hPersistence.createUpdateOperations(CVNGSchema.class);
    updateOperations.set(CVNGSchemaKeys.object, jsonString);
    hPersistence.update(hPersistence.createQuery(CVNGSchema.class), updateOperations);
  }

  private List<String> getSLIIdsFromSchemaObject() throws JsonProcessingException {
    CVNGSchema cvngSchema = hPersistence.createQuery(CVNGSchema.class).get();
    String retrievedJsonString = (String) cvngSchema.getObject();
    return objectMapper.readValue(retrievedJsonString, new TypeReference<List<String>>() {});
  }

  private void updateSLIIdsInSchemaObject(List<String> sliIds) throws JsonProcessingException {
    String jsonString = objectMapper.writeValueAsString(sliIds);
    final UpdateOperations<CVNGSchema> updateOperations = hPersistence.createUpdateOperations(CVNGSchema.class);
    updateOperations.set(CVNGSchemaKeys.object, jsonString);
    hPersistence.update(hPersistence.createQuery(CVNGSchema.class), updateOperations);
  }

  private List<String> fetchAllSLIIds() {
    List<ServiceLevelIndicator> serviceLevelIndicators = hPersistence.createQuery(ServiceLevelIndicator.class).asList();
    return serviceLevelIndicators.stream()
        .map(serviceLevelIndicator -> serviceLevelIndicator.getUuid())
        .collect(Collectors.toList());
  }
  private List<SLIRecord> processRecord(List<SLIRecord> currentBucket, SLIRecord sliRecord) {
    if (!currentBucket.isEmpty()
        && (currentBucket.get(currentBucket.size() - 1).getEpochMinute() + 1 == sliRecord.getEpochMinute())
        && (currentBucket.get(currentBucket.size() - 1).getSliId().equals(sliRecord.getSliId()))) {
      currentBucket.add(sliRecord);
      return currentBucket;
    }
    currentBucket = new ArrayList<>();
    if (sliRecord.getEpochMinute() % 5 == 0) {
      currentBucket.add(sliRecord);
    }
    return currentBucket;
  }

  private List<SLIRecord> processBucket(List<SLIRecord> currentBucket) {
    if (currentBucket.size() == 5) {
      SLIRecordBucket sliRecordBucket = SLIRecordBucket.getSLIRecordBucketFromSLIRecords(currentBucket);
      sliRecordBucketsToSave.add(sliRecordBucket);
      currentBucket = new ArrayList<>();
    }
    if (sliRecordBucketsToSave.size() >= BATCH_SIZE) {
      saveBuckets();
    }
    return currentBucket;
  }

  private void saveBuckets() {
    try {
      if (!sliRecordBucketsToSave.isEmpty()) {
        hPersistence.upsertBatch(SLIRecordBucket.class, sliRecordBucketsToSave, new ArrayList<>());
      }
      sliRecordBucketsToSave = new ArrayList<>();
    } catch (IllegalAccessException e) {
      throw new RuntimeException("[SLI Record Bucketing Error]", e);
    }
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
