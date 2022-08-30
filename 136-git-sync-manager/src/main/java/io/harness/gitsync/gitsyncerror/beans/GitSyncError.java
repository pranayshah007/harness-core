/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitsyncerror.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.EntityType;
import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeKeys;
import io.harness.data.validator.Trimmed;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.gitsyncerror.GitSyncErrorStatus;
import io.harness.gitsync.gitsyncerror.beans.GitToHarnessErrorDetails.GitToHarnessErrorDetailsKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@EqualsAndHashCode()
@FieldNameConstants(innerTypeName = "GitSyncErrorKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Document("gitSyncErrorNG")
@Entity(value = "gitSyncErrorNG")
@HarnessEntity(exportable = false)
@TypeAlias("io.harness.gitsync.gitsyncerror.beans.gitSyncError")
@OwnedBy(PL)
public class GitSyncError
    implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id private String uuid;
  @Trimmed @NotEmpty private String accountIdentifier;
  // The repo details about the git sync error repo
  private String repoUrl;
  private RepoProviders repoProvider;
  private String branchName;
  private List<Scope> scopes;

  // The details about the file in git
  private ChangeType changeType;
  private String completeFilePath;

  // The entity details
  private EntityType entityType;

  // The error details
  @NotEmpty private String failureReason;
  @NotNull private GitSyncErrorStatus status;
  @NotNull private GitSyncErrorType errorType;
  private GitSyncErrorDetails additionalErrorDetails;

  @CreatedBy private EmbeddedUser createdBy;
  @CreatedDate private long createdAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @LastModifiedDate private long lastUpdatedAt;

  @Builder
  public GitSyncError(String uuid, String accountIdentifier, String repoUrl, String branchName, List<Scope> scopes,
      ChangeType changeType, String completeFilePath, EntityType entityType, String failureReason,
      GitSyncErrorStatus status, GitSyncErrorType errorType, GitSyncErrorDetails additionalErrorDetails,
      EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy, long lastUpdatedAt,
      RepoProviders repoProvider) {
    this.uuid = uuid;
    this.accountIdentifier = accountIdentifier;
    this.repoUrl = repoUrl;
    this.branchName = branchName;
    this.scopes = scopes;
    this.changeType = changeType;
    this.completeFilePath = completeFilePath;
    this.entityType = entityType;
    this.failureReason = failureReason;
    this.status = status;
    this.errorType = errorType;
    this.additionalErrorDetails = additionalErrorDetails;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.lastUpdatedBy = lastUpdatedBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.repoProvider = repoProvider;
  }

  public static final class GitSyncErrorKeys {
    public static final String gitCommitId =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.gitCommitId;
    public static final String commitMessage =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.commitMessage;
    public static final String resolvedByCommitId =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.resolvedByCommitId;
    public static final String projectIdentifier = GitSyncErrorKeys.scopes + "." + ScopeKeys.projectIdentifier;
    public static final String orgIdentifier = GitSyncErrorKeys.scopes + "." + ScopeKeys.orgIdentifier;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_repo_branch_errorType_status_filePath_sort_Index")
                 .fields(Arrays.asList(GitSyncErrorKeys.accountIdentifier, GitSyncErrorKeys.repoUrl,
                     GitSyncErrorKeys.branchName, GitSyncErrorKeys.errorType, GitSyncErrorKeys.status,
                     GitSyncErrorKeys.completeFilePath))
                 .descSortField(GitSyncErrorKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_commitId_repo_branch_errorType_filePath_unique_Index")
                 .fields(Arrays.asList(GitSyncErrorKeys.accountIdentifier, GitSyncErrorKeys.gitCommitId,
                     GitSyncErrorKeys.repoUrl, GitSyncErrorKeys.branchName, GitSyncErrorKeys.errorType,
                     GitSyncErrorKeys.completeFilePath))
                 .unique(true)
                 .descSortField(GitSyncErrorKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_repo_errorType_status_sort_Index")
                 .fields(Arrays.asList(GitSyncErrorKeys.accountIdentifier, GitSyncErrorKeys.repoUrl,
                     GitSyncErrorKeys.errorType, GitSyncErrorKeys.status))
                 .descSortField(GitSyncErrorKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgIdentifier_projectIdentifier_Index")
                 .fields(Arrays.asList(GitSyncErrorKeys.accountIdentifier, GitSyncErrorKeys.projectIdentifier,
                     GitSyncErrorKeys.orgIdentifier))
                 .build())
        .build();
  }
}
