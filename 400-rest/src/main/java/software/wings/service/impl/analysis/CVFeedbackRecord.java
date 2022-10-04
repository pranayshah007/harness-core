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
import static io.harness.persistence.GoogleDataStoreAware.readBoolean;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static java.lang.System.currentTimeMillis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.serializer.JsonUtils;

import software.wings.security.ThreadLocalUserProvider;
import software.wings.service.impl.analysis.AnalysisServiceImpl.CLUSTER_TYPE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CVFeedbackRecordKeys")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = false)
@IgnoreUnusedIndex
@StoreIn(DbAliases.HARNESS)
@Entity(value = "cvFeedbackRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class CVFeedbackRecord implements GoogleDataStoreAware, AccountAccess {
  @Id private String uuid;
  @NotEmpty @FdIndex private String accountId;

  @NotEmpty @FdIndex private String serviceId;

  @NotEmpty @FdIndex private String envId;

  @FdIndex private String stateExecutionId;

  @FdIndex private String cvConfigId;

  @NotEmpty private int clusterLabel;

  @NotEmpty private AnalysisServiceImpl.CLUSTER_TYPE clusterType;

  @NotEmpty private String logMessage;

  private String comment;

  private String supervisedLabel;

  @Builder.Default private FeedbackPriority priority = FeedbackPriority.P2;

  private String jiraLink;

  private long analysisMinute;

  private FeedbackAction actionTaken;

  private String feedbackNote;

  private boolean isDuplicate;

  private long createdAt;
  private long lastUpdatedAt;

  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore private EmbeddedUser lastUpdatedBy;

  @PrePersist
  public void onSave() {
    if (uuid == null) {
      uuid = generateUuid();
    }

    EmbeddedUser embeddedUser = ThreadLocalUserProvider.threadLocalUser();
    if (createdBy == null) {
      createdBy = embeddedUser;
    }

    final long currentTime = currentTimeMillis();

    if (createdAt == 0) {
      createdAt = currentTime;
    }
    lastUpdatedAt = currentTime;
    lastUpdatedBy = embeddedUser;
  }

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    onSave();
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(this.getUuid() == null ? generateUuid() : this.getUuid());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);

    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.accountId, accountId, false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.envId, envId, false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.stateExecutionId, stateExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.cvConfigId, cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.clusterType, clusterType.name(), false);

    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.clusterLabel, clusterLabel, true);

    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.logMessage, logMessage, true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.supervisedLabel, supervisedLabel, true);

    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.priority, priority.name(), false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.analysisMinute, analysisMinute, true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.actionTaken, actionTaken.name(), false);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.priority, priority.name(), true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.jiraLink, jiraLink, false);

    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.createdAt, createdAt, true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.lastUpdatedAt, lastUpdatedAt, true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.createdBy, JsonUtils.asJson(createdBy), true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.lastUpdatedBy, JsonUtils.asJson(lastUpdatedBy), true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.isDuplicate, isDuplicate, true);
    addFieldIfNotEmpty(recordBuilder, CVFeedbackRecordKeys.feedbackNote, feedbackNote, true);
    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final CVFeedbackRecord record =
        CVFeedbackRecord.builder()
            .serviceId(readString(entity, CVFeedbackRecordKeys.serviceId))
            .envId(readString(entity, CVFeedbackRecordKeys.envId))
            .stateExecutionId(readString(entity, CVFeedbackRecordKeys.stateExecutionId))
            .cvConfigId(readString(entity, CVFeedbackRecordKeys.cvConfigId))
            .clusterLabel((int) (readLong(entity, CVFeedbackRecordKeys.clusterLabel)))
            .clusterType(CLUSTER_TYPE.valueOf(readString(entity, CVFeedbackRecordKeys.clusterType)))
            .logMessage(readString(entity, CVFeedbackRecordKeys.logMessage))
            .analysisMinute(readLong(entity, CVFeedbackRecordKeys.analysisMinute))
            .comment(readString(entity, CVFeedbackRecordKeys.comment))
            .actionTaken(FeedbackAction.valueOf(readString(entity, CVFeedbackRecordKeys.actionTaken)))
            .uuid(entity.getKey().getName())
            .priority(FeedbackPriority.valueOf(readString(entity, CVFeedbackRecordKeys.priority)))
            .jiraLink(readString(entity, CVFeedbackRecordKeys.jiraLink))
            .createdAt(readLong(entity, CVFeedbackRecordKeys.createdAt))
            .lastUpdatedAt(readLong(entity, CVFeedbackRecordKeys.lastUpdatedAt))
            .supervisedLabel(readString(entity, CVFeedbackRecordKeys.supervisedLabel))
            .accountId(readString(entity, CVFeedbackRecordKeys.accountId))
            .isDuplicate(readBoolean(entity, CVFeedbackRecordKeys.isDuplicate))
            .feedbackNote(readString(entity, CVFeedbackRecordKeys.feedbackNote))
            .build();

    String createdBy = readString(entity, CVFeedbackRecordKeys.createdBy);
    if (isNotEmpty(createdBy)) {
      record.setCreatedBy(JsonUtils.asObject(createdBy, EmbeddedUser.class));
    }
    String lastUpdatedBy = readString(entity, CVFeedbackRecordKeys.lastUpdatedBy);
    if (isNotEmpty(lastUpdatedBy)) {
      record.setLastUpdatedBy(JsonUtils.asObject(lastUpdatedBy, EmbeddedUser.class));
    }
    return record;
  }
}
