/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public enum OrchestrationAdviserTypes {
  // Provided From the orchestration layer system advisers

  // SUCCESS
  ON_SUCCESS,

  // NEXT_STEP
  NEXT_STEP,
  NEXT_STAGE,
  // FAILURES
  ON_FAIL,
  IGNORE,
  RETRY,

  ABORT,
  PAUSE,
  RESUME,
  MANUAL_INTERVENTION,

  MARK_SUCCESS,
  PROCEED_WITH_DEFAULT;
}
