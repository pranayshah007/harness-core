/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitDiffRequest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@BreakDependencyOn("software.wings.beans.GitConfig")
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class GitOperationContext {
  private String gitConnectorId;
  private GitConfig gitConfig;

  private GitCommitRequest gitCommitRequest;
  private GitDiffRequest gitDiffRequest;
  private boolean useGitFetchCommandTimeout;
}
