/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import io.harness.annotation.RecasterAlias;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@RecasterAlias("io.harness.git.model.FetchFilesResult")
public class FetchFilesResult extends GitBaseResult {
  private CommitResult commitResult;
  private List<GitFile> files;

  @Builder
  public FetchFilesResult(String accountId, CommitResult commitResult, List<GitFile> files) {
    super(accountId);
    this.commitResult = commitResult;
    this.files = files;
  }
}
