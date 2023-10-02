/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@RecasterAlias("io.harness.git.model.CommitResult")
public class CommitResult extends GitBaseResult {
  private String commitId;
  private int commitTime;
  private String commitMessage;

  @Builder
  public CommitResult(String accountId, String commitId, int commitTime, String commitMessage) {
    super(accountId);
    this.commitId = commitId;
    this.commitTime = commitTime;
    this.commitMessage = commitMessage;
  }
}
