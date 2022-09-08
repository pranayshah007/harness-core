/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.git.model.ChangeType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.GitRepositoryInfo;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

/**
 * @author vardanb
 */

@Data
@Builder
@FieldNameConstants(innerTypeName = "GitFileActivityKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gitFileActivity")
@HarnessEntity(exportable = false)
public class GitFileActivity implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_procCommitId_filePath_status")
                 .field(GitFileActivityKeys.accountId)
                 .field(GitFileActivityKeys.processingCommitId)
                 .field(GitFileActivityKeys.filePath)
                 .field(GitFileActivityKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_procCommitId_status")
                 .field(GitFileActivityKeys.accountId)
                 .field(GitFileActivityKeys.processingCommitId)
                 .field(GitFileActivityKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_filePath")
                 .field(GitFileActivityKeys.accountId)
                 .field(GitFileActivityKeys.filePath)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_commitId_Idx")
                 .field(GitFileActivityKeys.accountId)
                 .field(GitFileActivityKeys.commitId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_appId_createdAt_Idx")
                 .field(GitFileActivityKeys.accountId)
                 .field(GitFileActivityKeys.appId)
                 .descSortField(GitFileActivityKeys.createdAt)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String accountId;
  private String filePath;
  private String fileContent;
  private String commitId;
  private String processingCommitId;
  private ChangeType changeType;
  private String errorMessage;
  private Status status;
  private TriggeredBy triggeredBy;
  private boolean changeFromAnotherCommit;
  private String commitMessage;
  private String processingCommitMessage;
  private String appId;
  private long createdAt;
  private long lastUpdatedAt;
  private String gitConnectorId;
  private String repositoryName;
  private String branchName;
  @Transient private String connectorName;
  @Transient private GitRepositoryInfo repositoryInfo;
  @Transient @DefaultValue("false") private boolean userDoesNotHavePermForFile;

  public enum Status { SUCCESS, FAILED, DISCARDED, EXPIRED, SKIPPED, QUEUED }

  public enum TriggeredBy { USER, GIT, FULL_SYNC }

  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(12).toInstant());
}
