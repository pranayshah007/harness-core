/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitfileactivity.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.OrganizationAccess;
import io.harness.ng.core.ProjectAccess;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "GitFileActivityKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitFileActivityNG")
@Document("gitFileActivityNG")
@HarnessEntity(exportable = false)
@TypeAlias("io.harness.gitsync.gitfileactivity.beans.gitFileActivity")
@OwnedBy(DX)
public class GitFileActivity implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                        OrganizationAccess, ProjectAccess {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id private String uuid;
  private String accountId;
  private String organizationId;
  private String projectId;
  private String filePath;
  private String rootFilePath;
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
  private long createdAt;
  @FdIndex private long lastUpdatedAt;
  private String gitConnectorId;
  private String repo;
  private String rootFolder;
  private String branchName;

  public enum Status { SUCCESS, FAILED, DISCARDED, EXPIRED, SKIPPED, QUEUED }

  public enum TriggeredBy { USER, GIT, FULL_SYNC }
}
