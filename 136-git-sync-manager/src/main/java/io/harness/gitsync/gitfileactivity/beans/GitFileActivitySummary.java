/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitfileactivity.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus;
import io.harness.ng.DbAliases;
import io.harness.ng.core.OrganizationAccess;
import io.harness.ng.core.ProjectAccess;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@StoreIn(DbAliases.NG_MANAGER)
@Document("gitFileActivitySummaryNG")
@Entity(value = "gitFileActivitySummaryNG", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "GitFileActivitySummaryKeys")
@HarnessEntity(exportable = true)
@TypeAlias("io.harness.gitsync.gitfileactivity.beans.gitFileActivitySummary")
@OwnedBy(DX)
public class GitFileActivitySummary implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware,
                                               AccountAccess, OrganizationAccess, ProjectAccess {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id private String uuid;
  private String accountId;
  private String organizationId;
  private String projectId;
  private String commitId;
  private String branchName;
  private String repo;
  private String gitConnectorId;
  private long createdAt;
  private String commitMessage;
  private long lastUpdatedAt;
  private Boolean gitToHarness;
  private GitCommitProcessingStatus status;
  private GitFileProcessingSummary fileProcessingSummary;
}
