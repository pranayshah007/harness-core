/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.resourcerestraint.beans.HoldingScope")
public enum HoldingScope {
  // This is only for backward compatibility
  // TODO : Remove this after a release
  @Deprecated PLAN,

  // This corresponds to pipeline
  PIPELINE,

  // This corresponds to stage
  STAGE,

  STEP_GROUP
}
