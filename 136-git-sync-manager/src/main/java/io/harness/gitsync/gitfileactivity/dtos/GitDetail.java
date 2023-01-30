/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitfileactivity.dtos;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.EntityType;

import dev.morphia.annotations.Transient;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(DX)
public class GitDetail {
  private String entityName;
  private EntityType entityType;
  private String repositoryUrl;
  private String branchName;
  private String yamlGitConfigId;
  private String gitConnectorId;
  private String appId;
  private String gitCommitId;
  @Transient String connectorName;
}
