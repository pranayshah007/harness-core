/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readList;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static java.lang.System.currentTimeMillis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.GoogleDataStoreAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

/**
 * @author Praveen
 */
@Data
@Builder
@FieldNameConstants(innerTypeName = "LabeledLogRecordKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "supervisedLogRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class LabeledLogRecord implements GoogleDataStoreAware, AccountAccess {
  private String label;
  private String accountId;
  private Set<String> feedbackIds;
  private Set<String> logDataRecordIds;
  private long createdAt;
  private long lastUpdatedAt;
  private String serviceId;
  private String envId;
  @Id String uuid;

  @PrePersist
  public void onSave() {
    if (uuid == null) {
      uuid = generateUuid();
    }
    final long currentTime = currentTimeMillis();

    if (createdAt == 0) {
      createdAt = currentTime;
    }
    lastUpdatedAt = currentTime;
  }
  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    onSave();
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(this.uuid == null ? generateUuid() : this.uuid);
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.label, label, false);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.feedbackIds, feedbackIds, String.class);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.logDataRecordIds, logDataRecordIds, String.class);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.accountId, accountId, false);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.envId, envId, false);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.createdAt, createdAt, false);
    addFieldIfNotEmpty(recordBuilder, LabeledLogRecordKeys.lastUpdatedAt, lastUpdatedAt, false);

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final LabeledLogRecord dataRecord = LabeledLogRecord.builder()
                                            .uuid(entity.getKey().getName())
                                            .label(readString(entity, LabeledLogRecordKeys.label))
                                            .feedbackIds(Sets.newHashSet())
                                            .logDataRecordIds(Sets.newHashSet())
                                            .createdAt(readLong(entity, LabeledLogRecordKeys.createdAt))
                                            .lastUpdatedAt(readLong(entity, LabeledLogRecordKeys.lastUpdatedAt))
                                            .serviceId(readString(entity, LabeledLogRecordKeys.serviceId))
                                            .accountId(readString(entity, LabeledLogRecordKeys.accountId))
                                            .envId(readString(entity, LabeledLogRecordKeys.envId))
                                            .build();

    List<String> feedbackIds = readList(entity, LabeledLogRecordKeys.feedbackIds, String.class);
    if (isNotEmpty(feedbackIds)) {
      dataRecord.setFeedbackIds(Sets.newHashSet(feedbackIds));
    }
    List<String> dataRecordIds = readList(entity, LabeledLogRecordKeys.logDataRecordIds, String.class);
    if (isNotEmpty(dataRecordIds)) {
      dataRecord.setLogDataRecordIds(Sets.newHashSet(dataRecordIds));
    }

    return dataRecord;
  }
}
