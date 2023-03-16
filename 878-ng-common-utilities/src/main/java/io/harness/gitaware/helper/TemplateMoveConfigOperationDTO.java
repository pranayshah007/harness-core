/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
public class TemplateMoveConfigOperationDTO {
  String connectorRef;
  String repoName;
  String branch;
  String filePath;
  String baseBranch;
  String commitMessage;
  boolean isNewBranch;
  TemplateMoveConfigOperationType moveConfigOperationType;
}
