/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ngmigration.dto.RunStat;

import software.wings.beans.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class LogMigrationService {
  private static int DATA_STORE_BATCH_SIZE = 500;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                                                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                                        .registerModule(new Jdk8Module())
                                                        .registerModule(new GuavaModule())
                                                        .registerModule(new JavaTimeModule());
  private final Cache<String, RunStat> runIdToStats = Caffeine.newBuilder().maximumSize(100).build();
  public Map<String, RunStat> getStatus() {
    return runIdToStats.asMap();
  }

  private String getRunIdPrefixLog(String runId) {
    return String.format("RunId [%s]", runId);
  }

  public String migrate(String bucketName, String fileName) {
    String runId = UUIDGenerator.generateUuid();
    log.info("{} Execution started bucketName {} fileName {}", getRunIdPrefixLog(runId), bucketName, fileName);
    runIdToStats.put(runId, RunStat.builder().bucketName(bucketName).fileName(fileName).startTime(new Date()).build());

    new Thread(() -> {
      try {
        migrateInternal(bucketName, fileName, runId);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).start();

    return runId;
  }

  private void migrateInternal(String bucketName, String fileName, String runId) throws IOException {
    StorageOptions storageOptions = StorageOptions.getDefaultInstance();
    Storage storage = storageOptions.getService();

    Blob blob = storage.get(BlobId.of(bucketName, fileName));

    if (blob != null) {
      log.info("{} Found file", getRunIdPrefixLog(runId));
      // Open an InputStream to read the file
      try (BufferedReader reader = new BufferedReader(Channels.newReader(blob.reader(), StandardCharsets.UTF_8))) {
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        List<Entity> entityList = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
          runIdToStats.getIfPresent(runId).setProcessed(runIdToStats.getIfPresent(runId).getProcessed() + 1);
          Optional<Log> optionalLog = getLog(line);
          if (optionalLog.isEmpty()) {
            log.warn("{} Failed to create log object for line {}", getRunIdPrefixLog(runId), line);
            runIdToStats.getIfPresent(runId).setFailure(runIdToStats.getIfPresent(runId).getFailure() + 1);
          } else {
            Log commandLog = optionalLog.get();
            Entity entity = commandLog.convertToCloudStorageEntity(datastore);
            if (null == entity) {
              log.warn("{} Failed to create entity object for log {}", getRunIdPrefixLog(runId), commandLog);
              runIdToStats.getIfPresent(runId).setFailure(runIdToStats.getIfPresent(runId).getFailure() + 1);
            } else {
              Entity entityWithNewKey = Entity.newBuilder(getKey(commandLog, datastore), entity).build();
              entityList.add(entityWithNewKey);
            }

            if (entityList.size() == DATA_STORE_BATCH_SIZE) {
              pushToDatastore(runId, datastore, entityList);
            }
          }
        }

        if (!entityList.isEmpty()) {
          pushToDatastore(runId, datastore, entityList);
        }

      } catch (Exception ex) {
        log.error("{} Failed to initialise file stream", getRunIdPrefixLog(runId), ex);
      }
    } else {
      log.error("{} File {} not found in the bucket {}", getRunIdPrefixLog(runId), fileName, bucketName);
    }
    runIdToStats.getIfPresent(runId).setEndTime(new Date());
  }

  private void pushToDatastore(String runId, Datastore datastore, List<Entity> entityList) {
    try {
      datastore.put(entityList.toArray(Entity[] ::new));
      log.info("{} Successfully pushed {} count of logs to datastore", getRunIdPrefixLog(runId), entityList.size());
      runIdToStats.getIfPresent(runId).setSuccess(runIdToStats.getIfPresent(runId).getSuccess() + entityList.size());
    } catch (Exception ex) {
      log.info("{} Failed to push logs to datastore", getRunIdPrefixLog(runId), ex);
    }
    entityList.clear();
  }

  private Key getKey(Log log, Datastore datastore) {
    return datastore.newKeyFactory()
        .setKind(Log.class.getAnnotation(dev.morphia.annotations.Entity.class).value())
        .newKey(log.getUuid());
  }

  private Optional<Log> getLog(String line) {
    try {
      Log log = OBJECT_MAPPER.readValue(line, Log.class);
      log.setUuid(OBJECT_MAPPER.readTree(line).path("_id").asText());
      return Optional.of(log);
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
