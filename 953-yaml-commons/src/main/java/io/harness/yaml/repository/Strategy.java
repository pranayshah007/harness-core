/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.repository;

import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Strategy {
  @JsonProperty("merge-commit")
  MERGE_COMMIT {
    @Override
    public PRCloneStrategy toPRCloneStrategy() {
      return PRCloneStrategy.MERGE_COMMIT;
    }
  },
  @JsonProperty("source-branch")
  SOURCE_BRANCH {
    @Override
    public PRCloneStrategy toPRCloneStrategy() {
      return PRCloneStrategy.SOURCE_BRANCH;
    }
  };

  public abstract PRCloneStrategy toPRCloneStrategy();
}
