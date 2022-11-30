/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits.checkers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public interface LimitChecker {
  /**
   * Check if a particular action is allowed, and consume a permit if it is allowed.
   * If the limits need strict correctness (i.e off by one errors are not okay), the underlying implementation of this
   * should be an atomic operation in that case.
   *
   * @return whether the action is allowed or not. If the action is allowed, a unit will be consumed
   */
  boolean checkAndConsume();
}
