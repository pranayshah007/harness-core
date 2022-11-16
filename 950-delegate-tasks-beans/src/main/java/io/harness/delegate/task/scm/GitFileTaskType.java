/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.DX)
public enum GitFileTaskType {
  GET_FILE_CONTENT_BATCH,
  GET_FILE_CONTENT,
  GET_FILE_CONTENT_BATCH_BY_FILE_PATHS,
  GET_FILE_CONTENT_BATCH_BY_REF,
  GET_FILE,
  GET_FILE_GIT_DETAILS_LIST_IN_COMMIT,
}
