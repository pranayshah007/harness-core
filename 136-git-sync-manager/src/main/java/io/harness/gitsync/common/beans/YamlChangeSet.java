/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.core.beans.GitWebhookRequestAttributes;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "YamlChangeSetKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Document("yamlChangeSetNG")
@TypeAlias("io.harness.gitsync.common.beans.yamlChangeSet")
@Entity(value = "yamlChangeSetNG", noClassnameStored = true)
@OwnedBy(DX)
public class YamlChangeSet implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static final String MAX_RETRY_COUNT_EXCEEDED_CODE = "MAX_RETRY_COUNT_EXCEEDED";
  public static final String MAX_QUEUE_DURATION_EXCEEDED_CODE = "MAX_QUEUE_DURATION_EXCEEDED";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_retryCount_index")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.status)
                 .field(YamlChangeSetKeys.retryCount)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_index")
                 .field(YamlChangeSetKeys.accountId)
                 .descSortField(YamlChangeSetKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_createdAt_index")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.status)
                 .field(YamlChangeSetKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_queuekey_status_queuedOn_index")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.queueKey)
                 .field(YamlChangeSetKeys.status)
                 .descSortField(YamlChangeSetKeys.queuedOn)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String uuid;
  @Trimmed @NotEmpty @NotNull private String accountId;
  @FdIndex @NotNull private String status;
  private long queuedOn = System.currentTimeMillis();
  // todo: replace/modify with whatever comes from webhook svc
  private GitWebhookRequestAttributes gitWebhookRequestAttributes;
  @Default private Integer retryCount = 0;
  @NotNull private String queueKey;
  @NotNull private YamlChangeSetEventType eventType;
  private String messageCode;
  private String repoUrl;
  private String branch;
  private Long cutOffTime = 0L;

  // Any special event metadata which has to go back from queue as is can be pushed in this interface.
  EventMetadata eventMetadata;

  @EqualsAndHashCode.Exclude @CreatedDate private long createdAt;
  @EqualsAndHashCode.Exclude @LastModifiedDate private long lastUpdatedAt;

  @Builder
  public YamlChangeSet(String uuid, String accountId, String status, long queuedOn,
      GitWebhookRequestAttributes gitWebhookRequestAttributes, Integer retryCount, String queueKey,
      YamlChangeSetEventType eventType, String messageCode, String repoUrl, String branch, EventMetadata eventMetadata,
      long createdAt, long lastUpdatedAt) {
    this.uuid = uuid;
    this.accountId = accountId;
    this.status = status;
    this.queuedOn = queuedOn;
    this.gitWebhookRequestAttributes = gitWebhookRequestAttributes;
    this.retryCount = retryCount;
    this.queueKey = queueKey;
    this.eventType = eventType;
    this.messageCode = messageCode;
    this.repoUrl = repoUrl;
    this.branch = branch;
    this.eventMetadata = eventMetadata;
    this.createdAt = createdAt;
    this.lastUpdatedAt = lastUpdatedAt;
  }
}
