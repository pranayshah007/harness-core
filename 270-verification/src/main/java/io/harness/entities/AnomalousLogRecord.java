/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readBlob;
import static io.harness.persistence.GoogleDataStoreAware.readDouble;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.exception.WingsException;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.GoogleDataStoreAware;

import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * @author Praveen
 * 4/9/2019
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "AnomalousLogRecordsKey")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "anomalousLogRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class AnomalousLogRecord implements GoogleDataStoreAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("analysisMinIndex")
                 .unique(true)
                 .field(AnomalousLogRecordsKey.serviceId)
                 .field(AnomalousLogRecordsKey.cvConfigId)
                 .field(AnomalousLogRecordsKey.analysisMinute)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String cvConfigId;
  private long analysisMinute;
  private String serviceId;
  private String workflowId;

  private StateType stateType;
  private String logMessage;
  @Builder.Default private double riskLevel = 1.0;

  private long createdAt;
  private long lastUpdatedAt;

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(generateUuid());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    if (stateType != null) {
      addFieldIfNotEmpty(recordBuilder, "stateType", stateType.name(), true);
    }

    addFieldIfNotEmpty(recordBuilder, "serviceId", serviceId, false);
    addFieldIfNotEmpty(recordBuilder, "cvConfigId", cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, "analysisMinute", analysisMinute, false);

    addFieldIfNotEmpty(recordBuilder, "createdAt", createdAt == 0 ? System.currentTimeMillis() : createdAt, true);
    addFieldIfNotEmpty(recordBuilder, "lastUpdatedAt", System.currentTimeMillis(), true);

    try {
      Blob compressedLog = Blob.copyFrom(compressString(logMessage));
      addFieldIfNotEmpty(recordBuilder, "logMessage", compressedLog, true);
    } catch (Exception ex) {
      throw new WingsException("Exception while compressing and storing AnomalousLogRecord to GoogleDataStore");
    }

    recordBuilder.set("riskLevel", riskLevel);

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final AnomalousLogRecord dataRecord = AnomalousLogRecord.builder()
                                              .stateType(StateType.valueOf(readString(entity, "stateType")))
                                              .serviceId(readString(entity, "serviceId"))
                                              .cvConfigId(readString(entity, "cvConfigId"))
                                              .uuid(entity.getKey().getName())
                                              .analysisMinute(readLong(entity, "analysisMinute"))
                                              .workflowId(readString(entity, "workflowId"))
                                              .createdAt(readLong(entity, "createdAt"))
                                              .lastUpdatedAt(readLong(entity, "lastUpdatedAt"))
                                              .riskLevel(readDouble(entity, "riskLevel"))
                                              .build();

    try {
      byte[] byteCompressedMsg = readBlob(entity, "logMessage");
      dataRecord.setLogMessage(deCompressString(byteCompressedMsg));
    } catch (Exception ex) {
      throw new WingsException("Exception while decompressing and fetching AnomalousLogRecord from GoogleDataStore");
    }

    return dataRecord;
  }
}
