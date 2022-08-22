/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CI)
public enum CIContainerType {
  STEP_EXECUTOR(CIContainerSource.BUILD_JOB),
  ADD_ON(CIContainerSource.HARNESS_WORKER),
  RUN(CIContainerSource.HARNESS_WORKER),
  PLUGIN(CIContainerSource.HARNESS_WORKER),
  SERVICE(CIContainerSource.HARNESS_WORKER),
  LITE_ENGINE(CIContainerSource.HARNESS_WORKER),
  TEST_INTELLIGENCE(CIContainerSource.HARNESS_WORKER),
  BACKGROUND(CIContainerSource.HARNESS_WORKER);

  CIContainerSource ciContainerSource;

  CIContainerType(CIContainerSource ciContainerSource) {
    this.ciContainerSource = ciContainerSource;
  }
}
