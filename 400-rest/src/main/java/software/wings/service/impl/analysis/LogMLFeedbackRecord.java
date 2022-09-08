/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.GoogleDataStoreAware;

import software.wings.beans.Base;
import software.wings.service.impl.analysis.AnalysisServiceImpl.CLUSTER_TYPE;
import software.wings.service.impl.analysis.AnalysisServiceImpl.LogMLFeedbackType;
import software.wings.sm.StateType;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "LogMLFeedbackRecordKeys")
@IgnoreUnusedIndex
@StoreIn(DbAliases.HARNESS)
@Entity(value = "logMlFeedbackRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class LogMLFeedbackRecord extends Base implements GoogleDataStoreAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_log_feedback")
                 .unique(true)
                 .field("applicationId")
                 .field(LogMLFeedbackRecordKeys.stateExecutionId)
                 .field(LogMLFeedbackRecordKeys.clusterType)
                 .field(LogMLFeedbackRecordKeys.clusterLabel)
                 .build())
        .build();
  }

  @NotEmpty @FdIndex private String serviceId;

  private String envId;

  private String workflowId;

  @FdIndex private String workflowExecutionId;

  @FdIndex private String stateExecutionId;

  private StateType stateType;

  @NotEmpty private int clusterLabel;

  @NotEmpty private AnalysisServiceImpl.CLUSTER_TYPE clusterType;

  @NotEmpty private AnalysisServiceImpl.LogMLFeedbackType logMLFeedbackType;

  @NotEmpty private String logMessage;

  @NotEmpty private String logMD5Hash;

  private String cvConfigId;

  private String comment;

  private String supervisedLabel;

  private FeedbackPriority priority;

  private String jiraLink;

  private long analysisMinute;

  private FeedbackAction actionTaken;

  private Object metadata;

  @Builder
  public LogMLFeedbackRecord(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String serviceId, String workflowId,
      String workflowExecutionId, String stateExecutionId, StateType stateType, int clusterLabel,
      CLUSTER_TYPE clusterType, LogMLFeedbackType logMLFeedbackType, String logMessage, String logMD5Hash,
      String comment, String cvConfigId) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.serviceId = serviceId;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.stateExecutionId = stateExecutionId;
    this.stateType = stateType;
    this.clusterLabel = clusterLabel;
    this.clusterType = clusterType;
    this.logMLFeedbackType = logMLFeedbackType;
    this.logMessage = logMessage;
    this.logMD5Hash = logMD5Hash;
    this.comment = comment;
    this.cvConfigId = cvConfigId;
  }

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(this.getUuid() == null ? generateUuid() : this.getUuid());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, "appId", appId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.workflowId, workflowId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.workflowExecutionId, workflowExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.stateExecutionId, stateExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.cvConfigId, cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.clusterType, clusterType.name(), false);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.logMLFeedbackType, logMLFeedbackType.name(), true);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.logMD5Hash, logMD5Hash, true);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.logMessage, logMessage, true);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.supervisedLabel, supervisedLabel, true);
    addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.clusterLabel, clusterLabel, true);
    addFieldIfNotEmpty(recordBuilder, "createdAt", this.getCreatedAt(), true);
    addFieldIfNotEmpty(recordBuilder, "lastUpdatedAt", this.getLastUpdatedAt(), true);

    if (stateType != null) {
      addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.stateType, stateType.name(), true);
    }

    if (isNotEmpty(comment)) {
      addFieldIfNotEmpty(recordBuilder, LogMLFeedbackRecordKeys.comment, comment, true);
    }

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final LogMLFeedbackRecord dataRecord =
        LogMLFeedbackRecord.builder()
            .appId(readString(entity, "appId"))
            .uuid(entity.getKey().getName())
            .logMessage(readString(entity, LogMLFeedbackRecordKeys.logMessage))
            .logMD5Hash(readString(entity, LogMLFeedbackRecordKeys.logMD5Hash))
            .workflowId(readString(entity, LogMLFeedbackRecordKeys.workflowId))
            .workflowExecutionId(readString(entity, LogMLFeedbackRecordKeys.workflowExecutionId))
            .serviceId(readString(entity, LogMLFeedbackRecordKeys.serviceId))
            .stateExecutionId(readString(entity, LogMLFeedbackRecordKeys.stateExecutionId))
            .clusterLabel((int) readLong(entity, LogMLFeedbackRecordKeys.clusterLabel))
            .clusterType(CLUSTER_TYPE.valueOf(readString(entity, LogMLFeedbackRecordKeys.clusterType)))
            .logMLFeedbackType(LogMLFeedbackType.valueOf(readString(entity, LogMLFeedbackRecordKeys.logMLFeedbackType)))
            .cvConfigId(readString(entity, LogMLFeedbackRecordKeys.cvConfigId))
            .createdAt(readLong(entity, "createdAt"))
            .lastUpdatedAt(readLong(entity, "lastUpdatedAt"))
            .build();

    final String comment = readString(entity, LogMLFeedbackRecordKeys.comment);
    if (isNotEmpty(comment)) {
      dataRecord.setComment(comment);
    }

    final String stateType = readString(entity, LogMLFeedbackRecordKeys.stateType);
    if (isNotEmpty(stateType)) {
      dataRecord.setStateType(StateType.valueOf(stateType));
    }

    final String supervisedLabel = readString(entity, LogMLFeedbackRecordKeys.supervisedLabel);
    if (isNotEmpty(supervisedLabel)) {
      dataRecord.setSupervisedLabel(supervisedLabel);
    }

    return dataRecord;
  }
}
