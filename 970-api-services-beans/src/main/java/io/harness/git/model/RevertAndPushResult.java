/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RevertAndPushResult extends GitBaseResult {
  private CommitResult gitCommitResult;
  private PushResultGit gitPushResult;
  private List<GitFileChange> filesCommittedToGit;

  @Builder
  public RevertAndPushResult(String accountId, CommitResult gitCommitResult, PushResultGit gitPushResult,
      List<GitFileChange> filesCommittedToGit) {
    super(accountId);
    this.gitCommitResult = gitCommitResult;
    this.gitPushResult = gitPushResult;
    this.filesCommittedToGit = filesCommittedToGit;
  }
}
