/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.task.git.GitFetchFilesConfig")
public class GitFetchFilesConfig {
  String identifier;
  String manifestType;
  GitStoreDelegateConfig gitStoreDelegateConfig;
  boolean succeedIfFileNotFound;
}
