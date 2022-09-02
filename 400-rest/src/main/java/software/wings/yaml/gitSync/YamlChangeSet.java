/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;

import io.harness.ng.DbAliases;
import software.wings.beans.Base;
import software.wings.beans.yaml.GitFileChange;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

/**
 * @author bsollish 9/26/17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "YamlChangeSetKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "yamlChangeSet")
@HarnessEntity(exportable = false)
public class YamlChangeSet extends Base {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("searchIdx_1")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.status)
                 .field(YamlChangeSetKeys.retryCount)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_index")
                 .field(YamlChangeSetKeys.accountId)
                 .descSortField(Base.CREATED_AT_KEY)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_gitToHarness_createdAt_index")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.status)
                 .field(YamlChangeSetKeys.gitToHarness)
                 .field(Base.CREATED_AT_KEY)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_queuekey_status_createdAt_index")
                 .field(YamlChangeSetKeys.accountId)
                 .field(YamlChangeSetKeys.queueKey)
                 .field(YamlChangeSetKeys.status)
                 .descSortField(Base.CREATED_AT_KEY)
                 .build())
        .build();
  }

  public static final String MAX_RETRY_COUNT_EXCEEDED_CODE = "MAX_RETRY_COUNT_EXCEEDED";
  public static final String MAX_QUEUE_DURATION_EXCEEDED_CODE = "MAX_QUEUE_DURATION_EXCEEDED";

  @NotEmpty private String accountId;
  @NotNull private List<GitFileChange> gitFileChanges = new ArrayList<>();
  @FdIndex @NotNull private Status status;
  private boolean gitToHarness;
  private boolean forcePush;
  private long queuedOn = System.currentTimeMillis();
  private boolean fullSync;
  private String parentYamlChangeSetId;
  private GitWebhookRequestAttributes gitWebhookRequestAttributes;
  @Default private Integer retryCount = 0;
  private String messageCode;
  private String queueKey;
  @Default private Integer pushRetryCount = 0;
  private GitSyncMetadata gitSyncMetadata;

  public enum Status { QUEUED, RUNNING, FAILED, COMPLETED, SKIPPED }
  public static final List<Status> terminalStatusList =
      ImmutableList.of(Status.FAILED, Status.COMPLETED, Status.SKIPPED);

  @Builder
  public YamlChangeSet(String appId, String accountId, List<GitFileChange> gitFileChanges, Status status,
      boolean gitToHarness, boolean forcePush, long queuedOn, boolean fullSync, String parentYamlChangeSetId,
      GitWebhookRequestAttributes gitWebhookRequestAttributes, Integer retryCount, String messageCode, String queueKey,
      GitSyncMetadata gitSyncMetadata, Integer pushRetryCount) {
    this.appId = appId;
    this.accountId = accountId;
    this.gitFileChanges = gitFileChanges;
    this.status = status;
    this.gitToHarness = gitToHarness;
    this.forcePush = forcePush;
    this.queuedOn = queuedOn;
    this.fullSync = fullSync;
    this.parentYamlChangeSetId = parentYamlChangeSetId;
    this.gitWebhookRequestAttributes = gitWebhookRequestAttributes;
    this.retryCount = retryCount;
    this.messageCode = messageCode;
    this.queueKey = queueKey;
    this.gitSyncMetadata = gitSyncMetadata;
    this.pushRetryCount = pushRetryCount;
  }
}
