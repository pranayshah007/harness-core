/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync.entity;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.security.dto.UserPrincipal;

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
@Entity(value = "gitFullSyncJob", noClassnameStored = true)
@Document("gitFullSyncJob")
@TypeAlias("io.harness.gitsync.core.beans.gitFullSyncJob")
@FieldNameConstants(innerTypeName = "GitFullSyncJobKeys")
@OwnedBy(DX)
public class GitFullSyncJob implements PersistentEntity, PersistentRegularIterable {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id String uuid;
  @FdUniqueIndex String messageId;
  @NotEmpty @NotNull String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String yamlGitConfigIdentifier;
  String syncStatus;
  private boolean createPullRequest;
  String targetBranch;
  String branch;
  String prTitle;
  UserPrincipal triggeredBy;

  int retryCount;
  @FdIndex @NonFinal Long nextRuntime;
  List<String> errorMessage;

  @EqualsAndHashCode.Exclude @CreatedDate private long createdAt;
  @EqualsAndHashCode.Exclude @LastModifiedDate private long lastUpdatedAt;

  public enum SyncStatus { QUEUED, RUNNING, COMPLETED, FAILED_WITH_RETRIES_LEFT, FAILED }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (GitFullSyncJobKeys.nextRuntime.equals(fieldName)) {
      return nextRuntime;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (GitFullSyncJobKeys.nextRuntime.equals(fieldName)) {
      this.nextRuntime = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
