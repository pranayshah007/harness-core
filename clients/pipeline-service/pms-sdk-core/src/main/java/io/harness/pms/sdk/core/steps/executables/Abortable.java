/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.StepParameters;

@OwnedBy(CDC)
public interface Abortable<T extends StepParameters, V> {
  void handleAbort(Ambiance ambiance, T stepParameters, V executableResponse);
  default void handleAbortAndUserMarkedFailure(
      Ambiance ambiance, T stepParameters, V executableResponse, boolean isUserMarkedFailureInterrupt) {
    // By default, we are calling the handleAbort because in general steps handle the UserMarkedFailure in same way as
    // abort and steps had adopted the abort only.
    // But if any step want to differ the implementation for userMarkedFailure, they can override this method in the
    // step.
    handleAbort(ambiance, stepParameters, executableResponse);
  }
}
