/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

@OwnedBy(PIPELINE)
public enum MoveConfigOperationType {
  INLINE_TO_REMOTE,
  REMOTE_TO_INLINE;

  public static io.harness.pms.pipeline.MoveConfigOperationType getMoveConfigType(
      MoveConfigOperationType moveConfigOperationType) {
    if (MoveConfigOperationType.INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      return io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
    } else if (REMOTE_TO_INLINE.equals(moveConfigOperationType)) {
      return io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
    } else {
      throw new InvalidRequestException("Invalid move config type provided.");
    }
  }
}
