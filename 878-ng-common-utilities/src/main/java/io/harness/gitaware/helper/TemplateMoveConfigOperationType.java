/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

@OwnedBy(CDC)
public enum TemplateMoveConfigOperationType {
  INLINE_TO_REMOTE;

  public static TemplateMoveConfigOperationType getMoveConfigType(
      TemplateMoveConfigOperationType moveConfigOperationType) {
    if (TemplateMoveConfigOperationType.INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      return INLINE_TO_REMOTE;
    } else {
      throw new InvalidRequestException("Invalid move config type provided.");
    }
  }
}
