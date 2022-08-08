/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "VerificationTaskKeys")
@Entity(value = "verificationTasks", noClassnameStored = true)
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.CVNG)
public final class VerificationTask implements UuidAware, CreatedAtAware, AccountAccess, PersistentEntity {
  public static final String VERIFICATION_TASK_ID_KEY = "verificationTaskId";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("verification_job_instance_id_idx")
                 .field(VerificationTaskKeys.taskInfo + ".verificationJobInstanceId")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("cv_config_id_idx")
                 .field(VerificationTaskKeys.taskInfo + ".cvConfigId")
                 .build())
        .add(CompoundMongoIndex.builder().name("sli_id_idx").field(VerificationTaskKeys.taskInfo + ".sliId").build())
        .build();
  }

  @Builder
  public VerificationTask(
      Map<String, String> tags, String uuid, String accountId, long createdAt, Date validUntil, TaskInfo taskInfo) {
    this.tags = tags;
    this.uuid = uuid;
    this.accountId = accountId;
    this.createdAt = createdAt;
    this.validUntil = validUntil;
    this.taskInfo = taskInfo;
  }

  @Singular Map<String, String> tags;
  @Id private String uuid;
  @FdIndex private String accountId;
  @FdIndex private long createdAt;
  @FdTtlIndex private Date validUntil;
  // TODO: figure out a way to cleanup old/deleted mappings.
  private TaskInfo taskInfo;
  public Map<String, String> getTags() {
    if (tags == null) {
      return new HashMap<>();
    }
    return tags;
  }
  public abstract static class TaskInfo {
    public static final String TASK_TYPE_FIELD_NAME = "taskType";
    public abstract TaskType getTaskType();
  }

  @Value
  @Builder
  @FieldNameConstants(innerTypeName = "LiveMonitoringInfoKeys")
  public static class LiveMonitoringInfo extends TaskInfo {
    private final TaskType taskType = TaskType.LIVE_MONITORING;
    private String cvConfigId;
  }

  @Value
  @Builder
  @FieldNameConstants(innerTypeName = "DeploymentInfoKeys")
  public static class DeploymentInfo extends TaskInfo {
    private final TaskType taskType = TaskType.DEPLOYMENT;
    @NonNull private String cvConfigId;
    @NonNull private String verificationJobInstanceId;
  }

  @Value
  @Builder
  @FieldNameConstants(innerTypeName = "SLIInfoKeys")
  public static class SLIInfo extends TaskInfo {
    private final TaskType taskType = TaskType.SLI;
    @NonNull private String sliId;
  }

  public enum TaskType { LIVE_MONITORING, DEPLOYMENT, SLI }
}
