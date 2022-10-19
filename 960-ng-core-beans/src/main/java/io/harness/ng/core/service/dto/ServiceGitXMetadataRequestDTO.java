/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.dto;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;

import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class ServiceGitXMetadataRequestDTO {
  StoreType storeType;
  String repoName;
  String connectorRef;
  String repoURL;
  String branch;
  String filePath;
  boolean isCommitToNewBranch;
  String baseBranch;
  String commitMessage;

  // required in case of update operation
  String lastObjectId;
  String lastCommitId;
}
