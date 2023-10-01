/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum GitOperation {
  CREATE_FILE("create file"),
  UPDATE_FILE("update file"),
  GET_FILE("get file"),
  GET_REPO_URL("get repo url"),
  GET_BRANCH_HEAD_COMMIT("get branch head commit"),
  LIST_FILES("list files"),
  BG_THREAD_GET_FILE("background thread get file"),
  GET_BATCH_FILES("get batch files"),
  GET_USER_DETAILS("get user details"),
  VALIDATE_REPO("validate repo"),
  ;

  private String value;

  GitOperation(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static GitOperation fromValue(String value) {
    for (GitOperation operation : GitOperation.values()) {
      if (operation.value.equals(value)) {
        return operation;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
