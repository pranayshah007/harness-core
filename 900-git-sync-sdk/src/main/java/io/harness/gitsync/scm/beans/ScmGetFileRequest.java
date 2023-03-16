/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm.beans;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PIPELINE)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ScmGetFileRequest {
  Scope scope;
  String repoName;
  String branchName;
  String filePath;
  String connectorRef;
  boolean loadFromCache;
  EntityType entityType;
  Map<String, String> contextMap;
  boolean getOnlyFileContent;
}
