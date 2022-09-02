/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.errorhandling;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;

import io.harness.ng.DbAliases;
import software.wings.beans.Base;
import software.wings.beans.GitRepositoryInfo;
import software.wings.service.impl.yaml.GitSyncErrorStatus;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails.GitToHarnessErrorDetailsKeys;
import software.wings.yaml.errorhandling.HarnessToGitErrorDetails.HarnessToGitErrorDetailsKeys;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

/**
 * @author rktummala on 12/15/17
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "GitSyncErrorKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gitSyncError")
@HarnessEntity(exportable = false)
public class GitSyncError extends Base implements PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_filepath_direction_idx")
                 .unique(true)
                 .field(GitSyncErrorKeys.accountId)
                 .field(GitSyncErrorKeys.yamlFilePath)
                 .field(GitSyncErrorKeys.gitSyncDirection)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("gitCommitId_idx")
                 .field(GitSyncErrorKeys.accountId)
                 .field(GitSyncErrorKeys.gitSyncDirection)
                 .field(GitSyncErrorKeys.gitCommitId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("gitCommitId_idx_for_app_filter")
                 .field(GitSyncErrorKeys.accountId)
                 .field(Base.APP_ID_KEY2)
                 .field(GitSyncErrorKeys.gitSyncDirection)
                 .field(GitSyncErrorKeys.gitCommitId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("previousErrors_idx")
                 .field(GitSyncErrorKeys.accountId)
                 .field(GitSyncErrorKeys.gitSyncDirection)
                 .field(GitSyncErrorKeys.previousCommitIds)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("previousErrors_idx_for_app_filter")
                 .field(GitSyncErrorKeys.accountId)
                 .field(Base.APP_ID_KEY2)
                 .field(GitSyncErrorKeys.gitSyncDirection)
                 .field(GitSyncErrorKeys.previousCommitIds)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_createdAt")
                 .field(GitSyncErrorKeys.accountId)
                 .field(Base.CREATED_AT_KEY)
                 .build())
        .build();
  }

  private String accountId;
  private String yamlFilePath;
  private String changeType;
  private String failureReason;
  @Deprecated private String yamlContent;
  @Deprecated private String gitCommitId;
  @Setter @FdIndex private Long nextIteration;
  // TODO @deepak All other fields of this collection will be marked depreceated and will no longer be in db, but
  // fullSyncPath variable will be there in db as it is boolean, so will need one more migration to remove it
  private boolean fullSyncPath;
  @Deprecated private String lastAttemptedYaml;
  private GitSyncErrorStatus status;
  private String gitConnectorId;
  private String repositoryName;
  @Transient private String gitConnectorName;
  @Transient private GitRepositoryInfo repositoryInfo;

  private String branchName;
  private String yamlGitConfigId;
  @Deprecated private Long commitTime;
  private GitSyncErrorDetails additionalErrorDetails;
  private String gitSyncDirection;
  @Transient @DefaultValue("false") private boolean userDoesNotHavePermForFile;

  @Builder
  public GitSyncError(String accountId, String yamlFilePath, String changeType, String failureReason,
      String gitConnectorId, String branchName, String repositoryName, String yamlGitConfigId,
      GitSyncErrorDetails additionalErrorDetails, String gitSyncDirection, Long commitTime, String lastAttemptedYaml,
      boolean fullSyncPath, String yamlContent, String gitCommitId, GitSyncErrorStatus status,
      boolean userDoesNotHavePermForFile) {
    this.accountId = accountId;
    this.yamlFilePath = yamlFilePath;
    this.changeType = changeType;
    this.failureReason = failureReason;
    this.status = GitSyncErrorStatus.ACTIVE;
    this.gitConnectorId = gitConnectorId;
    this.branchName = branchName;
    this.repositoryName = repositoryName;
    this.yamlGitConfigId = yamlGitConfigId;
    this.additionalErrorDetails = additionalErrorDetails;
    this.gitSyncDirection = gitSyncDirection;
    this.commitTime = commitTime;
    this.lastAttemptedYaml = lastAttemptedYaml;
    this.fullSyncPath = fullSyncPath;
    this.yamlContent = yamlContent;
    this.gitCommitId = gitCommitId;
    this.status = status;
    this.userDoesNotHavePermForFile = userDoesNotHavePermForFile;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @UtilityClass
  public static final class GitSyncErrorKeys {
    public static final String gitCommitId =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.gitCommitId;
    public static final String commitTime =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.commitTime;
    public static final String fullSyncPath =
        GitSyncErrorKeys.additionalErrorDetails + "." + HarnessToGitErrorDetailsKeys.fullSyncPath;
    public static final String previousCommitIds =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.previousCommitIdsWithError;
    public static final String commitMessage =
        GitSyncErrorKeys.additionalErrorDetails + "." + GitToHarnessErrorDetailsKeys.commitMessage;
  }

  public enum GitSyncDirection { GIT_TO_HARNESS, HARNESS_TO_GIT }
}
