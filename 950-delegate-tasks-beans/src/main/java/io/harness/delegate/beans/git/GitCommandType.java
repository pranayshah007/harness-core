/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.git;

public enum GitCommandType {
  LIST_REMOTE,
  CLONE,
  CHECKOUT,
  DIFF,
  COMMIT,
  PUSH,
  PULL,
  COMMIT_AND_PUSH,
  FETCH_FILES,
  VALIDATE,
  FILES_BETWEEN_COMMITS,
  FETCH_FILES_FROM_MULTIPLE_REPO
}
