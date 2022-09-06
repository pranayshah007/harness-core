/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.EntityDetail.EntityDetailKeys;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitFullSyncEntityInfo", noClassnameStored = true)
@Document("gitFullSyncEntityInfo")
@TypeAlias("io.harness.gitsync.core.beans.gitFullSyncEntityInfo")
@FieldNameConstants(innerTypeName = "GitFullSyncEntityInfoKeys")
@OwnedBy(DX)
public class GitFullSyncEntityInfo implements PersistentEntity, PersistentRegularIterable {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id String uuid;
  String messageId;
  String fullSyncJobId;
  @NotEmpty @NotNull String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String filePath;
  String microservice;
  @NotNull EntityDetail entityDetail;
  String syncStatus;
  String repoUrl;
  String yamlGitConfigId;
  String repoName;
  String branchName;
  String rootFolder;
  int retryCount;
  @FdIndex @NonFinal Long nextRuntime;
  List<String> errorMessage;
  int fileProcessingSequenceNumber;

  @EqualsAndHashCode.Exclude @CreatedDate private long createdAt;
  @EqualsAndHashCode.Exclude @LastModifiedDate private long lastUpdatedAt;

  public enum SyncStatus { QUEUED, SUCCESS, FAILED, OVERRIDDEN }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (GitFullSyncEntityInfoKeys.nextRuntime.equals(fieldName)) {
      return nextRuntime;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (GitFullSyncEntityInfoKeys.nextRuntime.equals(fieldName)) {
      this.nextRuntime = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_entityType_status_filePath_name_repo_branch_idx")
                 .field(GitFullSyncEntityInfoKeys.accountIdentifier)
                 .field(GitFullSyncEntityInfoKeys.orgIdentifier)
                 .field(GitFullSyncEntityInfoKeys.projectIdentifier)
                 .field(GitFullSyncEntityInfoKeys.entityDetail + "." + EntityDetailKeys.type)
                 .field(GitFullSyncEntityInfoKeys.syncStatus)
                 .field(GitFullSyncEntityInfoKeys.filePath)
                 .field(GitFullSyncEntityInfoKeys.entityDetail + "." + EntityDetailKeys.name)
                 .field(GitFullSyncEntityInfoKeys.repoName)
                 .field(GitFullSyncEntityInfoKeys.branchName)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .build();
  }
}
