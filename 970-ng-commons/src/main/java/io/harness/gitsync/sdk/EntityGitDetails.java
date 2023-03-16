/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "EntityGitDetailsKeys")
@Schema(name = "EntityGitDetails", description = "This contains Git Details of the Entity")
@OwnedBy(DX)
public class EntityGitDetails {
  @Schema(description = "Object Id of the Entity") String objectId;
  @Schema(description = "Branch Name") String branch;
  @Schema(description = "Git Sync Config Id") String repoIdentifier;
  @Schema(description = "Root Folder Path of the Entity") String rootFolder;
  @Schema(description = "File Path of the Entity") String filePath;
  @Schema(description = "Name of the repo") String repoName;
  @Schema(description = "Latest Commit ID") String commitId;
  @Schema(description = "File Url of the entity") String fileUrl;
  @Schema(description = "Repo url of the entity") String repoUrl;
  @Schema(description = "Connector Reference of parent entity") String parentEntityConnectorRef;
  @Schema(description = "Repo name of parent entity") String parentEntityRepoName;
}
