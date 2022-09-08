/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.yaml.GitCommandResult;
import software.wings.yaml.gitSync.GitFileProcessingSummary;
import software.wings.yaml.gitSync.YamlChangeSet;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by bsollish 10/13/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gitCommits", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "GitCommitKeys")
public class GitCommit extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("gitCommitIdx")
                 .unique(true)
                 .field(GitCommitKeys.accountId)
                 .field(GitCommitKeys.commitId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitStatusLastUpdatedIdx")
                 .field(GitCommitKeys.accountId)
                 .field(GitCommitKeys.status)
                 .descSortField(GitCommitKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitAccountIdLastUpdatedAT")
                 .field(GitCommitKeys.accountId)
                 .descSortField(GitCommitKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitAccountIdCreatedAtDesc")
                 .field(GitCommitKeys.accountId)
                 .descSortField(GitCommitKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitAccountIdYamlConfigIdsStatusLastUpdatedIdx")
                 .field(GitCommitKeys.accountId)
                 .field(GitCommitKeys.yamlGitConfigIds)
                 .field(GitCommitKeys.status)
                 .descSortField(GitCommitKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitAccountIdStatusYgcLastUpdatedIdx")
                 .field(GitCommitKeys.accountId)
                 .field(GitCommitKeys.status)
                 .field(GitCommitKeys.yamlGitConfigIds)
                 .descSortField(GitCommitKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("gitCommitAccountIdStatusYgLastUpdatedIdx")
                 .field(GitCommitKeys.accountId)
                 .field(GitCommitKeys.status)
                 .field(GitCommitKeys.yamlGitConfigId)
                 .descSortField(GitCommitKeys.lastUpdatedAt)
                 .build())
        .build();
  }

  private String accountId;
  private String yamlGitConfigId;
  private String commitId;
  private YamlChangeSet yamlChangeSet;
  private GitCommandResult gitCommandResult;
  @FdIndex private Status status;
  private FailureReason failureReason;
  private List<String> yamlChangeSetsProcessed;
  private List<String> yamlGitConfigIds;
  private GitFileProcessingSummary fileProcessingSummary;
  private String commitMessage;
  private String gitConnectorId;
  private String repositoryName;
  private String branchName;

  public enum Status { QUEUED, RUNNING, COMPLETED, FAILED, COMPLETED_WITH_ERRORS, SKIPPED }

  public enum FailureReason {
    GIT_CONNECTION_FAILED,
    GIT_CLONE_FAILED,
    GIT_PUSH_FAILED,
    GIT_PULL_FAILED,
    COMMIT_PARSING_FAILED
  }

  public static final List<Status> GIT_COMMIT_PROCESSED_STATUS =
      ImmutableList.of(Status.COMPLETED, Status.COMPLETED_WITH_ERRORS);

  public static final List<Status> GIT_COMMIT_ALL_STATUS_LIST = ImmutableList.<GitCommit.Status>builder()
                                                                    .addAll(GIT_COMMIT_PROCESSED_STATUS)
                                                                    .add(Status.FAILED)
                                                                    .add(Status.SKIPPED)
                                                                    .build();

  @UtilityClass
  public static final class GitCommitKeys {
    // Temporary
    public static final String lastUpdatedAt = "lastUpdatedAt";
    public static final String createdAt = "createdAt";
    public static final String gitFileChanges = "yamlChangeSet.gitFileChanges";
  }
}
